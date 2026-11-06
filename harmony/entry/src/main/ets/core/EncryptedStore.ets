import { huks } from '@kit.UniversalKeystoreKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

const KEY_ALIAS: string = 'smu_private_data_v1';
const NONCE_LENGTH: number = 12;
/** GCM 认证标签长度（解密时通过 HUKS_TAG_AE_TAG 单独传入） */
const AE_TAG_LENGTH: number = 16;
const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a01;

interface CodedError {
  code?: number;
  message: string;
}

/** BusinessError 的 code/message 不可枚举，JSON.stringify 只能打出 {}，这里手动取 */
export function errorText(e: Object): string {
  if (e instanceof Error) {
    const coded = e as CodedError;
    return `code=${coded.code ?? -1}, message=${coded.message}`;
  }
  return String(e);
}

/**
 * 加密落盘存储：HUKS 托管 AES-256-GCM 密钥，密文写应用私有文件。
 * 对应 Android 端 EncryptedStore（Keystore + AES-GCM + AtomicFile）。
 *
 * - 密钥由 HUKS 生成并保管，永不出安全区；
 * - 每次加密使用新的随机 12 字节 nonce；
 * - 文件布局：[12B nonce][密文 + 16B GCM tag]；
 * - store 名作为 GCM AAD，隔离 Cookie 与课程缓存两份数据；
 * - 密文损坏或密钥丢失（换机/重装）时丢弃不可用数据，不回退明文。
 */
export class EncryptedStore {
  private readonly filePath: string;
  private readonly aad: Uint8Array;

  constructor(context: common.Context, name: string) {
    this.filePath = `${context.filesDir}/${name}.enc`;
    this.aad = EncryptedStore.encodeUtf8(name);
  }

  async read(): Promise<string | null> {
    try {
      if (!fs.accessSync(this.filePath)) {
        return null;
      }
      const file = fs.openSync(this.filePath, fs.OpenMode.READ_ONLY);
      try {
        const stat = fs.statSync(this.filePath);
        const buffer = new ArrayBuffer(stat.size);
        fs.readSync(file.fd, buffer);
        const bytes = new Uint8Array(buffer);
        if (bytes.length <= NONCE_LENGTH + AE_TAG_LENGTH) {
          return null;
        }
        const plain = await this.decrypt(bytes.slice(0, NONCE_LENGTH), bytes.slice(NONCE_LENGTH));
        return EncryptedStore.decodeUtf8(plain);
      } finally {
        fs.closeSync(file);
      }
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `EncryptedStore 读取失败，丢弃不可用数据: ${errorText(e)}`);
      this.clear();
      return null;
    }
  }

  async write(value: string): Promise<void> {
    const cipher = await this.encrypt(EncryptedStore.encodeUtf8(value));
    const file = fs.openSync(this.filePath,
      fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      const buffer = new ArrayBuffer(cipher.byteLength);
      new Uint8Array(buffer).set(cipher);
      fs.writeSync(file.fd, buffer);
    } finally {
      fs.closeSync(file);
    }
  }

  clear(): void {
    try {
      if (fs.accessSync(this.filePath)) {
        fs.unlinkSync(this.filePath);
      }
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `EncryptedStore 清理失败: ${errorText(e)}`);
    }
  }

  // -------------------------------------------------------------------
  // HUKS AES-256-GCM（按官方 AES/GCM/NoPadding 示例：initSession + finishSession 两段式）
  // -------------------------------------------------------------------

  /**
   * 首次运行生成密钥是「查存在 → 生成」两步异步操作；登录轮询会对多个 host
   * 并发保存 cookie，两个并发加密会同时判定「密钥不存在」然后重复 generateKeyItem，
   * 后到的那个直接失败（表现为「无法安全保存登录凭据」）。
   * 全类共享一把 in-flight 锁：无论多少并发，密钥只生成一次；
   * 失败时释放锁，下次调用重试。
   */
  private static keyInit: Promise<void> | null = null;

  private async ensureKey(): Promise<void> {
    if (EncryptedStore.keyInit === null) {
      EncryptedStore.keyInit = EncryptedStore.initKey().catch((e: Object) => {
        EncryptedStore.keyInit = null;
        throw e instanceof Error ? e : new Error(String(e));
      });
    }
    await EncryptedStore.keyInit;
  }

  private static async initKey(): Promise<void> {
    const options: huks.HuksOptions = { properties: [] };
    let exist = false;
    try {
      exist = await huks.isKeyItemExist(KEY_ALIAS, options);
    } catch (e) {
      // 实测（模拟器）：key 不存在时 isKeyItemExist 直接 reject
      // （12000011 The entity does not exist），而不是按文档 resolve(false)
      exist = false;
    }
    if (exist) {
      return;
    }
    try {
      await EncryptedStore.generateKey();
    } catch (e) {
      // HUKS 里可能残留损坏的同名密钥（isKeyItemExist 说不存在、generate 又说冲突，
      // 并发首装或密钥文件残缺时会出现）：删掉重建。
      // 旧密文本来也解不开了，read 路径已按「损坏即丢弃」处理
      hilog.warn(DOMAIN, TAG, `HUKS 密钥损坏，删除重建: ${errorText(e)}`);
      try {
        await huks.deleteKeyItem(KEY_ALIAS, options);
      } catch (e2) {
        // 删除失败不挡路，重建再试一次
      }
      await EncryptedStore.generateKey();
    }
  }

  private static async generateKey(): Promise<void> {
    await huks.generateKeyItem(KEY_ALIAS, {
      properties: [
        { tag: huks.HuksTag.HUKS_TAG_ALGORITHM, value: huks.HuksKeyAlg.HUKS_ALG_AES },
        { tag: huks.HuksTag.HUKS_TAG_KEY_SIZE, value: huks.HuksKeySize.HUKS_AES_KEY_SIZE_256 },
        {
          tag: huks.HuksTag.HUKS_TAG_PURPOSE,
          value: huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_ENCRYPT | huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_DECRYPT
        },
      ]
    });
  }

  private gcmProperties(purpose: number, nonce: Uint8Array): huks.HuksParam[] {
    return [
      { tag: huks.HuksTag.HUKS_TAG_ALGORITHM, value: huks.HuksKeyAlg.HUKS_ALG_AES },
      { tag: huks.HuksTag.HUKS_TAG_KEY_SIZE, value: huks.HuksKeySize.HUKS_AES_KEY_SIZE_256 },
      { tag: huks.HuksTag.HUKS_TAG_PURPOSE, value: purpose },
      { tag: huks.HuksTag.HUKS_TAG_PADDING, value: huks.HuksKeyPadding.HUKS_PADDING_NONE },
      { tag: huks.HuksTag.HUKS_TAG_BLOCK_MODE, value: huks.HuksCipherMode.HUKS_MODE_GCM },
      { tag: huks.HuksTag.HUKS_TAG_NONCE, value: nonce },
      { tag: huks.HuksTag.HUKS_TAG_ASSOCIATED_DATA, value: this.aad },
    ];
  }

  /** 数据量小（几 KB 级），不需要 updateSession 分段：init + finish 一次完成 */
  private async encrypt(plain: Uint8Array): Promise<Uint8Array> {
    await this.ensureKey();
    const nonce = cryptoFramework.createRandom().generateRandomSync(NONCE_LENGTH).data;
    const options: huks.HuksOptions = {
      properties: this.gcmProperties(huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_ENCRYPT, nonce),
      inData: plain,
    };
    const session = await huks.initSession(KEY_ALIAS, options);
    let cipher: Uint8Array;
    try {
      // outData = 密文 + 末尾 16 字节 GCM tag
      const result = await huks.finishSession(session.handle, options);
      cipher = result.outData ?? new Uint8Array(0);
    } catch (e) {
      await EncryptedStore.abortQuietly(session.handle, options);
      throw new Error(`encrypt failed: ${(e as Error).message ?? e}`);
    }

    const out = new Uint8Array(NONCE_LENGTH + cipher.length);
    out.set(nonce, 0);
    out.set(cipher, NONCE_LENGTH);
    return out;
  }

  private async decrypt(nonce: Uint8Array, cipherWithTag: Uint8Array): Promise<Uint8Array> {
    await this.ensureKey();
    // 官方 GCM 解密要求：密文（不含 tag）进 inData，tag 走 HUKS_TAG_AE_TAG 属性
    const body = cipherWithTag.slice(0, cipherWithTag.length - AE_TAG_LENGTH);
    const aeTag = cipherWithTag.slice(cipherWithTag.length - AE_TAG_LENGTH);
    const properties = this.gcmProperties(huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_DECRYPT, nonce);
    properties.push({ tag: huks.HuksTag.HUKS_TAG_AE_TAG, value: aeTag });

    const options: huks.HuksOptions = {
      properties: properties,
      inData: body,
    };
    const session = await huks.initSession(KEY_ALIAS, options);
    try {
      const result = await huks.finishSession(session.handle, options);
      return result.outData ?? new Uint8Array(0);
    } catch (e) {
      await EncryptedStore.abortQuietly(session.handle, options);
      throw new Error(`decrypt failed: ${(e as Error)?.message ?? String(e)}`);
    }
  }

  private static async abortQuietly(handle: number, options: huks.HuksOptions): Promise<void> {
    try {
      await huks.abortSession(handle, options);
    } catch (e) {
      // 中止失败不影响主流程
    }
  }

  private static encodeUtf8(value: string): Uint8Array {
    return util.TextEncoder.create('utf-8').encodeInto(value);
  }

  private static decodeUtf8(bytes: Uint8Array): string {
    return util.TextDecoder.create('utf-8').decodeToString(bytes);
  }
}

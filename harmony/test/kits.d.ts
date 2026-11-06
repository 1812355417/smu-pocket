// 鸿蒙 Kit 的最小类型桩，仅用于在 Linux 上对纯逻辑层做 tsc 类型检查。
declare module '@kit.NetworkKit' {
  export namespace http {
    export enum RequestMethod { GET, POST }
    export enum HttpDataType { STRING }
    export interface HttpRequestOptions {
      method?: RequestMethod;
      header?: Object;
      extraData?: string | Object;
      expectDataType?: HttpDataType;
      connectTimeout?: number;
      readTimeout?: number;
    }
    export interface HttpResponse {
      responseCode: number;
      header: Object;
      result: string | Object;
      cookies?: string;
    }
    export interface HttpRequest {
      request(url: string, options?: HttpRequestOptions): Promise<HttpResponse>;
      destroy(): void;
    }
    export function createHttp(): HttpRequest;
  }
}

declare module '@kit.ArkData' {
  export namespace preferences {
    export interface Options { name: string }
    export interface Preferences {
      getSync(key: string, defValue: Object): Object;
      putSync(key: string, value: Object): void;
      deleteSync(key: string): void;
      flush(): void;
    }
    export function getPreferencesSync(context: Object, options: Options): Preferences;
  }
}

declare module '@kit.CoreFileKit' {
  export namespace fileIo {
    export enum OpenMode { READ_ONLY = 0, READ_WRITE = 1, CREATE = 256, TRUNC = 1024 }
    export interface File { fd: number }
    export interface Stat { size: number }
    export function openSync(path: string, mode?: number): File;
    export function closeSync(file: File): void;
    export function readSync(fd: number, buffer: ArrayBuffer, options?: Object): number;
    export function writeSync(fd: number, buffer: ArrayBuffer | string, options?: Object): number;
    export function statSync(path: string): Stat;
    export function accessSync(path: string): boolean;
    export function unlinkSync(path: string): void;
  }
}

declare module '@kit.UniversalKeystoreKit' {
  export namespace huks {
    export enum HuksTag { HUKS_TAG_ALGORITHM, HUKS_TAG_KEY_SIZE, HUKS_TAG_PURPOSE, HUKS_TAG_PADDING, HUKS_TAG_BLOCK_MODE, HUKS_TAG_NONCE, HUKS_TAG_ASSOCIATED_DATA, HUKS_TAG_AE_TAG }
    export enum HuksKeyAlg { HUKS_ALG_AES }
    export enum HuksKeySize { HUKS_AES_KEY_SIZE_256 }
    export enum HuksKeyPurpose { HUKS_KEY_PURPOSE_ENCRYPT = 1, HUKS_KEY_PURPOSE_DECRYPT = 2 }
    export enum HuksKeyPadding { HUKS_PADDING_NONE }
    export enum HuksCipherMode { HUKS_MODE_GCM }
    export interface HuksParam { tag: HuksTag; value: number | boolean | Uint8Array }
    export interface HuksOptions { properties: HuksParam[]; inData?: Uint8Array }
    export interface HuksSessionHandle { handle: number; challenge: Uint8Array }
    export function isKeyItemExist(keyAlias: string, options: HuksOptions): Promise<boolean>;
    export function generateKeyItem(keyAlias: string, options: HuksOptions): Promise<void>;
    export function deleteKeyItem(keyAlias: string, options: HuksOptions): Promise<void>;
    export interface HuksReturnResult { outData?: Uint8Array }
    export function initSession(keyAlias: string, options: HuksOptions): Promise<HuksSessionHandle>;
    export function updateSession(handle: number, options: HuksOptions): Promise<HuksReturnResult>;
    export function finishSession(handle: number, options: HuksOptions): Promise<HuksReturnResult>;
    export function abortSession(handle: number, options: HuksOptions): Promise<void>;
  }
}

declare module '@kit.CryptoArchitectureKit' {
  export namespace cryptoFramework {
    export interface DataBlob { data: Uint8Array }
    export interface Random { generateRandomSync(len: number): DataBlob }
    export function createRandom(): Random;
  }
}

declare module '@kit.ArkTS' {
  export namespace util {
    export interface TextEncoder { encodeInto(input?: string): Uint8Array }
    export interface TextDecoder { decodeToString(input: Uint8Array): string }
    export namespace util {
    }
    export const TextEncoder: { create(encoding?: string): TextEncoder };
    export const TextDecoder: { create(encoding?: string): TextDecoder };
  }
}

declare module '@kit.AbilityKit' {
  export namespace common {
    export interface Context { filesDir: string }
  }
}

declare module '@kit.PerformanceAnalysisKit' {
  export namespace hilog {
    export function info(domain: number, tag: string, format: string, ...args: Object[]): void;
    export function warn(domain: number, tag: string, format: string, ...args: Object[]): void;
    export function error(domain: number, tag: string, format: string, ...args: Object[]): void;
  }
}

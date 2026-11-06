import { common } from '@kit.AbilityKit';
import { EncryptedStore } from './EncryptedStore';

const HOST_PATTERN: RegExp = /^https?:\/\/([^/:?#]+)/i;
const URL_PATTERN: RegExp = /^(https?):\/\/([^/:?#]+)(:\d+)?(\/[^?#]*)?/i;
/** 合并后的 Set-Cookie 拆分点：逗号后面跟着「新 cookie 名=」才算新的一条（Expires 里的逗号不满足） */
const SET_COOKIE_SPLIT: RegExp = /,(?=\s*[^\s,;=]+=)/;

/** 从 URL 里取 host；取不到返回 null（等价于 Android 端的 CookieHarvester.hostOf） */
export function hostOf(url: string): string | null {
  const m = HOST_PATTERN.exec(url.trim());
  return m ? m[1].toLowerCase() : null;
}

export interface UrlParts {
  scheme: string;
  host: string;
  path: string;
}

/** 拆出 cookie 匹配要用的三段；path 缺省补 "/" */
export function urlParts(url: string): UrlParts | null {
  const m = URL_PATTERN.exec(url.trim());
  if (!m) {
    return null;
  }
  const path = m[4] ?? '/';
  return {
    scheme: m[1].toLowerCase(),
    host: m[2].toLowerCase(),
    path: path.length > 0 ? path : '/',
  };
}

/**
 * 一条 cookie 的全貌（对齐 OkHttp Cookie 里影响收发的属性）。
 * Android 端靠 okhttp3.Cookie 自动处理这些；鸿蒙要自己管，否则：
 * - 服务端注销时发的 Max-Age=0 删不掉本地 JSESSIONID，一直拿过期凭证打接口；
 * - CAS 落在父域（Domain=.smu.edu.cn）的 cookie 不会发给教务子域；
 * - 过期 cookie 照发不误。
 */
export interface CookieEntry {
  value: string;
  /** true = 只发回原 host（无 Domain 属性）；false = 父域 cookie，子域共享 */
  hostOnly: boolean;
  path: string;
  secure: boolean;
  /** epoch 毫秒；null = 会话 cookie，不过期 */
  expiresAt: number | null;
}

export type CookieTable = Map<string, Map<string, CookieEntry>>;

export interface ParsedSetCookie {
  /** 存储域（表的 key）：无 Domain 属性时是请求 host，有则按 RFC 6265 校验后的 Domain */
  domain: string;
  name: string;
  entry: CookieEntry;
  /** true = 服务端要求删除（Max-Age<=0 或 Expires 已过期），入库前先删同名 */
  expired: boolean;
}

/** 平台把多条 Set-Cookie 合并成一个字符串时的拆分（逗号合并 / 换行合并都兜住） */
export function splitSetCookie(raw: string): string[] {
  const out: string[] = [];
  const lines = raw.split(/\r?\n/);
  for (const line of lines) {
    const pieces = line.split(SET_COOKIE_SPLIT);
    for (const piece of pieces) {
      const trimmed = piece.trim();
      if (trimmed.length > 0) {
        out.push(trimmed);
      }
    }
  }
  return out;
}

/** RFC 6265 §5.1.4：请求路径没给 Path 属性时的默认 Path（取到最后一个 "/" 为止） */
function defaultPath(requestPath: string): string {
  if (!requestPath.startsWith('/')) {
    return '/';
  }
  const last = requestPath.lastIndexOf('/');
  return last <= 0 ? '/' : requestPath.substring(0, last);
}

/** RFC 6265 §5.1.3 的 path-match：前缀命中后，cookiePath 以 / 结尾或下一字符是 / */
function pathMatches(requestPath: string, cookiePath: string): boolean {
  if (requestPath === cookiePath) {
    return true;
  }
  if (requestPath.startsWith(cookiePath)) {
    return cookiePath.endsWith('/') || requestPath.charAt(cookiePath.length) === '/';
  }
  return false;
}

/**
 * 解析一条 Set-Cookie（不含换行/合并串，先过 splitSetCookie）。
 * Domain 必须是请求 host 的后缀否则忽略该属性（OkHttp 同样拒绝跨域盖章）。
 */
export function parseSetCookie(line: string, requestHost: string, requestPath: string,
  now: number): ParsedSetCookie | null {
  const parts = line.split(';');
  const first = parts[0];
  const i = first.indexOf('=');
  if (i <= 0) {
    return null;
  }
  const name = first.substring(0, i).trim();
  const value = first.substring(i + 1).trim();
  if (name.length === 0) {
    return null;
  }

  let domain = requestHost;
  let hostOnly = true;
  let path = defaultPath(requestPath);
  let secure = false;
  let expiresAt: number | null = null;
  let maxAge: number | null = null;

  for (let k = 1; k < parts.length; k++) {
    const attr = parts[k].trim();
    const eq = attr.indexOf('=');
    const attrName = (eq > 0 ? attr.substring(0, eq) : attr).trim().toLowerCase();
    const attrValue = eq > 0 ? attr.substring(eq + 1).trim() : '';
    if (attrName === 'domain') {
      let d = attrValue.toLowerCase();
      if (d.startsWith('.')) {
        d = d.substring(1);
      }
      if (d.length > 0 && (requestHost === d || requestHost.endsWith('.' + d))) {
        domain = d;
        hostOnly = false;
      }
    } else if (attrName === 'path') {
      if (attrValue.startsWith('/')) {
        path = attrValue;
      }
    } else if (attrName === 'max-age') {
      const seconds = parseInt(attrValue, 10);
      if (!isNaN(seconds)) {
        maxAge = seconds;
      }
    } else if (attrName === 'expires') {
      // 老格式 "21-Oct-2015" 的连字符 Date.parse 不认，换成空格
      const t = Date.parse(attrValue.replace(/-/g, ' '));
      if (!isNaN(t)) {
        expiresAt = t;
      }
    } else if (attrName === 'secure') {
      secure = true;
    }
  }

  // Max-Age 优先级高于 Expires（RFC 6265 §5.2.2）
  if (maxAge !== null) {
    expiresAt = maxAge > 0 ? now + maxAge * 1000 : 0;
  }
  const expired = expiresAt !== null && expiresAt <= now;
  return {
    domain: domain,
    name: name,
    entry: { value: value, hostOnly: hostOnly, path: path, secure: secure, expiresAt: expiresAt },
    expired: expired,
  };
}

/** 一条 cookie 该不该发给这个 URL（过期 / 域 / 路径 / Secure 四项检查） */
export function cookieMatches(parts: UrlParts, domain: string, entry: CookieEntry,
  now: number): boolean {
  if (entry.expiresAt !== null && entry.expiresAt <= now) {
    return false;
  }
  if (entry.hostOnly) {
    if (parts.host !== domain) {
      return false;
    }
  } else if (parts.host !== domain && !parts.host.endsWith('.' + domain)) {
    return false;
  }
  if (entry.secure && parts.scheme !== 'https') {
    return false;
  }
  return pathMatches(parts.path, entry.path);
}

/** 按 URL 过滤出该发的 cookie，拼成 Cookie 请求头 */
export function buildCookieHeader(tables: CookieTable, url: string, now: number): string {
  const parts = urlParts(url);
  if (parts === null) {
    return '';
  }
  const out: string[] = [];
  tables.forEach((table, domain) => {
    table.forEach((entry, name) => {
      if (cookieMatches(parts, domain, entry, now)) {
        out.push(`${name}=${entry.value}`);
      }
    });
  });
  return out.join('; ');
}

/**
 * 登录态（cookie）存储。对应 Android 端 core/Session.kt + okhttp3.Cookie 的收发语义。
 *
 * 关键设计：
 * - **密码永不出现在这里**。密码只在学校登录页（ArkWeb）里输入，
 *   我们只接管登录成功后的 cookie。验证码、密码前端加密、CAS 多跳、
 *   扫码登录全部免适配。
 * - 按域分别存，CAS 和教务互不干扰；Domain 父域 cookie 子域共享。
 * - Cookie 只通过 HUKS + AES-GCM 加密保存，不保存明文。
 */
export class SessionStore {
  private readonly storage: EncryptedStore;
  private readonly cache: CookieTable = new Map();
  private acceptingResponses: boolean = false;

  private constructor(storage: EncryptedStore) {
    this.storage = storage;
  }

  static async create(context: common.Context): Promise<SessionStore> {
    const store = new SessionStore(new EncryptedStore(context, 'session'));
    const raw = await store.storage.read();
    if (raw !== null) {
      try {
        const parsed = JSON.parse(raw) as Record<string, Record<string, Object>>;
        for (const domain of Object.keys(parsed)) {
          const table = new Map<string, CookieEntry>();
          const entries = parsed[domain];
          for (const name of Object.keys(entries)) {
            const entry = SessionStore.readEntry(entries[name]);
            if (entry !== null) {
              table.set(name, entry);
            }
          }
          store.cache.set(domain, table);
        }
      } catch (e) {
        store.storage.clear();
        store.cache.clear();
      }
    }
    store.acceptingResponses = Array.from(store.cache.values()).some((t) => t.size > 0);
    return store;
  }

  /**
   * 反序列化一条 cookie。兼容两种历史格式：
   * - v1：直接是 string（只有 value，按 host-only 会话 cookie 还原）
   * - v2：{value, hostOnly, path, secure, expiresAt}
   */
  private static readEntry(raw: Object): CookieEntry | null {
    if (typeof raw === 'string') {
      return { value: raw, hostOnly: true, path: '/', secure: false, expiresAt: null };
    }
    if (typeof raw !== 'object' || raw === null) {
      return null;
    }
    const rec = raw as Record<string, Object>;
    const value = rec['value'];
    if (typeof value !== 'string') {
      return null;
    }
    const path = rec['path'];
    const expiresAt = rec['expiresAt'];
    return {
      value: value,
      hostOnly: rec['hostOnly'] !== false,
      path: typeof path === 'string' && path.startsWith('/') ? path : '/',
      secure: rec['secure'] === true,
      expiresAt: typeof expiresAt === 'number' ? expiresAt : null,
    };
  }

  /** 拼成 HTTP 的 Cookie 请求头（过期 / 域 / 路径 / Secure 不匹配的不发） */
  cookieHeader(url: string): string {
    return buildCookieHeader(this.cache, url, Date.now());
  }

  /** WebView 登录成功后拿到的一整串 cookie，直接灌进来（视为 host-only 会话 cookie） */
  async saveRawCookieHeader(url: string, header: string): Promise<void> {
    const host = hostOf(url);
    if (host === null || header.trim().length === 0) {
      return;
    }
    this.acceptingResponses = true;
    const table = this.table(host);
    for (const pair of header.split(';')) {
      const i = pair.indexOf('=');
      if (i > 0) {
        const name = pair.substring(0, i).trim();
        if (name.length > 0) {
          table.set(name, {
            value: pair.substring(i + 1).trim(),
            hostOnly: true,
            path: '/',
            secure: false,
            expiresAt: null,
          });
        }
      }
    }
    await this.persist();
  }

  /**
   * 回收响应里的 Set-Cookie（可能是一条字符串、合并串或多条数组）。
   * 教务用 JSESSIONID 会话 cookie，登录后服务端会刷新它；
   * 服务端发 Max-Age=0 / 过期 Expires 是「删除」，本地同步删掉。
   */
  async saveSetCookies(url: string, setCookie: string | string[] | undefined): Promise<void> {
    if (!this.acceptingResponses || setCookie === undefined) {
      return;
    }
    const parts = urlParts(url);
    if (parts === null) {
      return;
    }
    const rawLines = Array.isArray(setCookie) ? setCookie : [setCookie];
    const now = Date.now();
    let changed = false;
    for (const raw of rawLines) {
      for (const line of splitSetCookie(raw)) {
        const parsed = parseSetCookie(line, parts.host, parts.path, now);
        if (parsed === null) {
          continue;
        }
        const table = this.table(parsed.domain);
        if (parsed.expired) {
          if (table.delete(parsed.name)) {
            changed = true;
          }
          continue;
        }
        table.set(parsed.name, parsed.entry);
        changed = true;
      }
    }
    if (changed) {
      await this.persist();
    }
  }

  isLoggedIn(): boolean {
    return Array.from(this.cache.values()).some((t) => t.size > 0);
  }

  async clear(): Promise<void> {
    this.acceptingResponses = false;
    this.cache.clear();
    this.storage.clear();
  }

  private table(domain: string): Map<string, CookieEntry> {
    let t = this.cache.get(domain);
    if (!t) {
      t = new Map<string, CookieEntry>();
      this.cache.set(domain, t);
    }
    return t;
  }

  /** 对齐 Android：写盘失败说明加密通道坏了，清空内存态并抛出，不能假装存上了 */
  private async persist(): Promise<void> {
    const out: Record<string, Record<string, CookieEntry>> = {};
    this.cache.forEach((table, domain) => {
      const entries: Record<string, CookieEntry> = {};
      table.forEach((entry, name) => {
        entries[name] = entry;
      });
      out[domain] = entries;
    });
    try {
      await this.storage.write(JSON.stringify(out));
    } catch (e) {
      this.acceptingResponses = false;
      this.cache.clear();
      this.storage.clear();
      throw e instanceof Error ? e : new Error(String(e));
    }
  }
}

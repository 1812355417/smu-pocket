import { http } from '@kit.NetworkKit';
import { SessionStore } from './Session';

const UA: string = 'SMUPocket/1.0 (HarmonyOS)';
const CONNECT_TIMEOUT_MS: number = 15000;
const READ_TIMEOUT_MS: number = 20000;
/** 对齐 OkHttp 的 MAX_FOLLOW_UPS */
const MAX_FOLLOW_UPS: number = 20;

/** 一次请求的描述。刻意做得薄：教务是表单 POST，电费可能是 GET + token */
export interface Req {
  url: string;
  /** 默认 GET。当前只用 GET / POST（教务是表单），需要更多方法时再扩 */
  method?: string;
  headers?: Record<string, string>;
  /** application/x-www-form-urlencoded */
  form?: Record<string, string>;
  /** application/json 原始体 */
  json?: string;
  referer?: string;
}

/** 一次响应。url 是重定向链走完后的最终落点（没重定向就是请求 URL） */
export class Resp {
  readonly code: number;
  readonly body: string;
  readonly contentType: string | null;
  readonly url: string;

  constructor(code: number, body: string, contentType: string | null, url: string) {
    this.code = code;
    this.body = body;
    this.contentType = contentType;
    this.url = url;
  }

  get ok(): boolean {
    return this.code >= 200 && this.code < 300;
  }

  /** 教务「登录失效」的典型表现：302 到登录页，或直接返回登录页 HTML */
  get looksLikeLoginPage(): boolean {
    if (this.code === 401 || this.code === 403) {
      return true;
    }
    const ct = this.contentType;
    return ct !== null && ct.toLowerCase().indexOf('text/html') >= 0 &&
      this.body.toLowerCase().indexOf('login') >= 0;
  }
}

/**
 * 传输层策略。这是整个项目最重要的一个抽象点（对应 Android 端 core/Transport.kt）。
 *
 * - [HttpTransport]：教务系统。带 cookie 直接打，最快。
 * - WebTransport（电费/一卡通，后续再实现）：页面自己算签名/token，我们只截响应。
 *
 * 为什么必须抽象：参考项目的电费就是因为拿不到 WebView 里的会话，
 * 最后改成在页面里劫持 XMLHttpRequest。如果一开始把具体实现写进 ViewModel，
 * 这个改动会波及整个 UI 层。
 */
export interface Transport {
  readonly label: string;
  exec(req: Req): Promise<Resp>;
}

/** application/x-www-form-urlencoded 编码 */
export function encodeForm(form: Record<string, string>): string {
  const parts: string[] = [];
  for (const key of Object.keys(form)) {
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(form[key])}`);
  }
  return parts.join('&');
}

/**
 * 解析重定向 Location：绝对地址直接用；协议相对补 scheme；
 * 根相对补 origin；相对路径补当前目录（对齐 OkHttp/浏览器行为）。
 */
export function resolveHttpUrl(location: string, base: string): string | null {
  const loc = location.trim();
  if (loc.length === 0) {
    return null;
  }
  if (/^https?:\/\//i.test(loc)) {
    return loc;
  }
  const m = /^(https?):\/\/([^/?#]+)([^?#]*)/i.exec(base);
  if (!m) {
    return null;
  }
  const scheme = m[1].toLowerCase();
  const origin = `${scheme}://${m[2]}`;
  const basePath = m[3].length > 0 ? m[3] : '/';
  if (loc.startsWith('//')) {
    return `${scheme}:${loc}`;
  }
  if (loc.startsWith('/')) {
    return origin + loc;
  }
  if (loc.startsWith('?') || loc.startsWith('#')) {
    return origin + basePath + loc;
  }
  const dir = basePath.substring(0, basePath.lastIndexOf('/') + 1);
  return origin + dir + loc;
}

interface RawResp {
  code: number;
  body: string;
  contentType: string | null;
  location: string | null;
}

/**
 * @kit.NetworkKit 传输实现（对应 Android 端 core/Net.kt 的 OkHttpTransport）。
 *
 * - cookie 手动带上（SessionStore）、Set-Cookie 手动回收；
 * - OkHttp 的 followRedirects 在这里是手动跟：顺手把每一跳的 Set-Cookie 收下来、
 *   Cookie 带过去，还能记住最终落点 URL 给失效检测用；
 * - retryOnConnectionFailure：网络层失败整体重试一次。
 */
export class HttpTransport implements Transport {
  readonly label: string = 'http';
  private readonly session: SessionStore;

  constructor(session: SessionStore) {
    this.session = session;
  }

  async exec(req: Req): Promise<Resp> {
    try {
      return await this.follow(req, 0);
    } catch (e) {
      // 连接失败重试一次（对齐 OkHttp retryOnConnectionFailure）
      return await this.follow(req, 0);
    }
  }

  /** 手动跟随重定向：301/302/303 降成 GET 丢 body；307/308 保持原方法和 body */
  private async follow(req: Req, depth: number): Promise<Resp> {
    const raw = await this.execOnce(req);
    if (raw.code >= 300 && raw.code < 400 && depth < MAX_FOLLOW_UPS && raw.location !== null) {
      const next = resolveHttpUrl(raw.location, req.url);
      if (next !== null) {
        const downgrade = raw.code === 301 || raw.code === 302 || raw.code === 303;
        const nextReq: Req = {
          url: next,
          method: downgrade ? 'GET' : req.method,
          headers: req.headers,
          form: downgrade ? undefined : req.form,
          json: downgrade ? undefined : req.json,
          referer: req.referer,
        };
        return this.follow(nextReq, depth + 1);
      }
    }
    return new Resp(raw.code, raw.body, raw.contentType, req.url);
  }

  private async execOnce(req: Req): Promise<RawResp> {
    const method = HttpTransport.methodOf(req);
    const header: Record<string, string> = {};
    if (req.headers !== undefined) {
      for (const key of Object.keys(req.headers)) {
        header[key] = req.headers[key];
      }
    }
    HttpTransport.setDefaultHeader(header, 'User-Agent', UA);
    if (req.referer !== undefined) {
      HttpTransport.setDefaultHeader(header, 'Referer', req.referer);
    }

    const body = HttpTransport.bodyOf(req, method);
    if (body.contentType !== null) {
      HttpTransport.setDefaultHeader(header, 'Content-Type', body.contentType);
    }
    const cookie = this.session.cookieHeader(req.url);
    if (cookie.length > 0) {
      header['Cookie'] = cookie;
    }

    const client = http.createHttp();
    try {
      const response = await client.request(req.url, {
        method: method,
        header: header,
        extraData: body.body,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: CONNECT_TIMEOUT_MS,
        readTimeout: READ_TIMEOUT_MS,
      });

      // 回收 Set-Cookie：响应头里的优先；Harmony 的 response.cookies 兜底
      const setCookie = HttpTransport.extractSetCookie(response.header) ?? response.cookies;
      if (setCookie !== undefined) {
        await this.session.saveSetCookies(req.url, setCookie);
      }

      return {
        code: response.responseCode,
        body: typeof response.result === 'string'
          ? response.result
          : JSON.stringify(response.result ?? ''),
        contentType: HttpTransport.headerValue(response.header, 'content-type') ?? null,
        location: HttpTransport.headerValue(response.header, 'location') ?? null,
      };
    } finally {
      client.destroy();
    }
  }

  private static methodOf(req: Req): http.RequestMethod {
    return (req.method ?? 'GET').toUpperCase() === 'POST'
      ? http.RequestMethod.POST
      : http.RequestMethod.GET;
  }

  /** OkHttp 要求 POST 必须有 body；Harmony 允许空，这里补空串对齐语义 */
  private static bodyOf(req: Req, method: http.RequestMethod): BodyInfo {
    if (req.form !== undefined) {
      return { body: encodeForm(req.form), contentType: 'application/x-www-form-urlencoded' };
    }
    if (req.json !== undefined) {
      return { body: req.json, contentType: 'application/json; charset=utf-8' };
    }
    if (method === http.RequestMethod.POST) {
      return { body: '', contentType: null };
    }
    return { body: undefined, contentType: null };
  }

  /** 头名大小写不敏感地「只补缺、不覆盖」 */
  private static setDefaultHeader(header: Record<string, string>, name: string,
    value: string): void {
    if (HttpTransport.headerValueRaw(header, name) === undefined) {
      header[name] = value;
    }
  }

  private static extractSetCookie(header: Object | undefined): string | string[] | undefined {
    const value = HttpTransport.headerValueRaw(header, 'set-cookie');
    if (value === undefined) {
      return undefined;
    }
    if (typeof value === 'string') {
      return value;
    }
    if (Array.isArray(value)) {
      return (value as Object[]).map((v) => String(v));
    }
    return String(value);
  }

  private static headerValue(header: Object | undefined, name: string): string | undefined {
    const raw = HttpTransport.headerValueRaw(header, name);
    if (raw === undefined) {
      return undefined;
    }
    return Array.isArray(raw) ? String((raw as Object[])[0] ?? '') : String(raw);
  }

  private static headerValueRaw(header: Object | undefined, name: string): Object | undefined {
    if (header === undefined || header === null || typeof header !== 'object') {
      return undefined;
    }
    const record = header as Record<string, Object>;
    const lower = name.toLowerCase();
    for (const key of Object.keys(record)) {
      if (key.toLowerCase() === lower) {
        return record[key];
      }
    }
    return undefined;
  }
}

interface BodyInfo {
  body: string | undefined;
  contentType: string | null;
}

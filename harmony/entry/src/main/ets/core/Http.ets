import { Req, Transport } from './Transport';

const LOGIN_URL_HINTS: string[] = ['login', '/cas/', 'sso', 'authserver', 'passport'];

/** 登录态失效。ViewModel 抓到它就清 cookie、把用户送回扫码页 */
export class SessionExpiredException extends Error {
  constructor(message: string = '登录态失效，请重新扫码登录') {
    super(message);
    this.name = 'SessionExpiredException';
  }
}

/** 网络 / 协议错误 */
export class HttpException extends Error {
  readonly statusCode: number;

  constructor(statusCode: number, message: string = '') {
    super(message.length > 0 ? message : `HTTP ${statusCode}`);
    this.name = 'HttpException';
    this.statusCode = statusCode;
  }
}

/**
 * 教务系统的门面（对应 Android 端 core/JwClient.kt 的两个拦截器）。
 *
 * 传输细节（cookie、重定向、重试）全部下沉到 Transport；这里只做教务语义：
 * - BrowserHeadersInterceptor：补齐「浏览器会发、但代码容易漏」的头
 *   （Referer / X-Requested-With / Accept），只补缺、不覆盖。
 *   缺了教务会把请求当爬虫，返回 {"code":-401,"疑似非法请求"}；
 * - SessionExpiredInterceptor：登录态失效检测。cookie 过期后教务**不会**返回 401，
 *   而是 302 到登录页然后回 200 + 一整页登录 HTML —— 这里统一翻译成
 *   SessionExpiredException，看「最终 URL + 响应体」比看单次 302 稳。
 */
export class JwHttp {
  private readonly transport: Transport;
  private readonly referer: string;

  constructor(transport: Transport, referer: string) {
    this.transport = transport;
    this.referer = referer;
  }

  /** POST application/x-www-form-urlencoded，返回响应体文本 */
  async postForm(url: string, form: Record<string, string>): Promise<string> {
    return this.exec({ url: url, method: 'POST', form: form });
  }

  /** GET（登录页探测等轻量用途） */
  async get(url: string): Promise<string> {
    return this.exec({ url: url, method: 'GET' });
  }

  private async exec(req: Req): Promise<string> {
    // BrowserHeadersInterceptor：只补缺，不覆盖调用方显式设置的头
    const headers: Record<string, string> = {};
    if (req.headers !== undefined) {
      for (const key of Object.keys(req.headers)) {
        headers[key] = req.headers[key];
      }
    }
    JwHttp.setDefault(headers, 'X-Requested-With', 'XMLHttpRequest');
    JwHttp.setDefault(headers, 'Accept', 'application/json, text/javascript, */*; q=0.01');

    const full: Req = {
      url: req.url,
      method: req.method,
      headers: headers,
      form: req.form,
      json: req.json,
      referer: req.referer ?? this.referer,
    };
    const resp = await this.transport.exec(full);

    // SessionExpiredInterceptor（对齐 Android 端 isLoginPage）
    if (resp.code === 401 || resp.code === 403) {
      throw new SessionExpiredException();
    }
    // 重定向链的最终落点是登录页：凭证已经被踢了
    if (JwHttp.looksLikeLoginUrl(resp.url)) {
      throw new SessionExpiredException();
    }
    if (!resp.ok) {
      throw new HttpException(resp.code);
    }

    // 200 但其实是登录页 HTML（被 302 踢回来后跟着跟到了登录页）；
    // 再看一眼有没有密码框，避免把正常页面误判
    const contentType = (resp.contentType ?? '').toLowerCase();
    if (contentType.indexOf('text/html') >= 0) {
      const head = resp.body.substring(0, 4096).toLowerCase();
      if (head.indexOf('type="password"') >= 0 || head.indexOf("type='password'") >= 0) {
        throw new SessionExpiredException();
      }
    }

    return resp.body;
  }

  private static looksLikeLoginUrl(url: string): boolean {
    const lower = url.toLowerCase();
    return LOGIN_URL_HINTS.some((hint) => lower.indexOf(hint) >= 0);
  }

  private static setDefault(headers: Record<string, string>, name: string, value: string): void {
    const lower = name.toLowerCase();
    for (const key of Object.keys(headers)) {
      if (key.toLowerCase() === lower) {
        return;
      }
    }
    headers[name] = value;
  }
}

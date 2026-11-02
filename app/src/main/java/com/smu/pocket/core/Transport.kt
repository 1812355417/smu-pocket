package com.smu.pocket.core

/** 一次请求的描述。刻意做得薄：教务是表单 POST，电费可能是 GET + token */
data class Req(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    /** application/x-www-form-urlencoded */
    val form: Map<String, String>? = null,
    /** application/json 原始体 */
    val json: String? = null,
    val referer: String? = null,
)

data class Resp(
    val code: Int,
    val body: String,
    val contentType: String? = null,
) {
    val ok: Boolean get() = code in 200..299
    /** 教务「登录失效」的典型表现：302 到登录页，或直接返回登录页 HTML */
    val looksLikeLoginPage: Boolean
        get() = code == 401 || code == 403 ||
            (contentType?.contains("text/html", true) == true && body.contains("login", true))
}

/**
 * 传输层策略。这是整个项目最重要的一个抽象点。
 *
 * - [OkHttpTransport]：教务系统。带 cookie 直接打，最快。
 * - WebViewTransport（P5 再实现）：电费/一卡通。页面自己算签名/token，我们只截响应。
 *
 * 为什么必须抽象：参考项目（广中医）的电费就是因为拿不到 WebView 里的会话，
 * 最后改成在页面里劫持 XMLHttpRequest。如果一开始把 OkHttp 写进 ViewModel，
 * 这个改动会波及整个 UI 层。
 */
interface Transport {
    val label: String
    suspend fun exec(req: Req): Resp
}

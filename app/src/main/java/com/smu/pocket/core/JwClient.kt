package com.smu.pocket.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit

/** 登录态失效。ViewModel 抓到它就清 cookie、把用户送回扫码页 */
class SessionExpiredException(message: String = "登录态失效，请重新扫码登录") : IOException(message)

/**
 * 补上「浏览器会发、但代码容易漏」的头。
 *
 * EasyUI / 强智 / 正方这类教务系统经常校验 Referer：缺了就把你当爬虫，
 * 返回登录页 HTML 而不是数据（表现是「明明 cookie 是对的，就是拿不到数据」）。
 */
class BrowserHeadersInterceptor(private val referer: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("X-Requested-With") == null) {
            builder.header("X-Requested-With", "XMLHttpRequest")
        }
        if (original.header("Referer") == null) {
            builder.header("Referer", referer)
        }
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json, text/javascript, */*; q=0.01")
        }
        return chain.proceed(builder.build())
    }
}

/**
 * 登录态失效检测。
 *
 * 这是最容易漏的一环：cookie 过期后，教务**不会**返回 401，
 * 而是 302 到登录页然后回一个 200 + 一整页登录 HTML。
 * 如果你的代码只看 `response.isSuccessful`，就会拿一页 HTML 去解析，
 * 报出「JSON 解析失败」这种误导性错误。
 */
class SessionExpiredInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isLoginPage()) {
            response.close()
            throw SessionExpiredException()
        }
        return response
    }
}

private fun Response.isLoginPage(): Boolean {
    if (code == 401 || code == 403) return true

    val finalUrl = request.url.toString()
    if (LOGIN_URL_HINTS.any { finalUrl.contains(it, ignoreCase = true) }) return true

    val contentType = header("Content-Type").orEmpty()
    if (!contentType.contains("text/html", ignoreCase = true)) return false

    // 返回 HTML 基本就是被踢回登录页；再看一眼有没有密码框，避免把正常页面误判
    val head = peekBody(4096).string()
    return head.contains("type=\"password\"", ignoreCase = true) ||
        head.contains("type='password'", ignoreCase = true)
}

private val LOGIN_URL_HINTS = listOf("login", "/cas/", "sso", "authserver", "passport")

object JwClient {

    /**
     * 教务专用 OkHttp。要点：
     * - [SessionCookieJar]：cookie 自动带上、自动回收 Set-Cookie，业务代码零干预
     * - [BrowserHeadersInterceptor]：补齐 Referer / X-Requested-With
     * - [SessionExpiredInterceptor]：把「200 + 登录页 HTML」变成可捕获的异常
     *
     * 关于 followRedirects：这里**保持 true**。关掉虽然能直接看到 302，
     * 但正常的尾斜杠跳转 / http→https 跳转也会一起断掉，得不偿失。
     * 失效检测交给 [SessionExpiredInterceptor] 看「最终 URL + 响应体」更稳。
     */
    fun okHttp(
        session: SessionStore,
        referer: String,
        connectTimeoutSeconds: Long = 15,
        readTimeoutSeconds: Long = 20,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar(session))
        .addInterceptor(BrowserHeadersInterceptor(referer))
        .addInterceptor(SessionExpiredInterceptor())
        .followRedirects(true)
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    fun retrofit(session: SessionStore, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl) // 必须以 / 结尾
        .client(okHttp(session, referer = baseUrl))
        .build()
}

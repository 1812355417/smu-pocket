package com.smu.pocket.core

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 教务系统的传输实现：OkHttp + cookie 复用。
 *
 * 刻意不用 Retrofit —— 教务是 application/x-www-form-urlencoded 的杂表单，
 * 响应还可能是一整页 HTML，Retrofit 的接口注解帮不上忙，反而多一层。
 */
class OkHttpTransport(private val session: SessionStore) : Transport {

    override val label: String = "okhttp"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar(session))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun exec(req: Req): Resp = withContext(Dispatchers.IO) {
        val method = req.method.uppercase(Locale.ROOT)
        val form = req.form
        val json = req.json

        val body: RequestBody? = when {
            form != null -> FormBody.Builder().apply {
                form.forEach { (k, v) -> add(k, v) }
            }.build()

            json != null -> json.toRequestBody(JSON)

            method == "GET" || method == "HEAD" -> null

            // OkHttp 要求 POST 必须有 body
            else -> ByteArray(0).toRequestBody(null)
        }

        val builder = Request.Builder()
            .url(req.url)
            .header("User-Agent", UA)
            .method(method, body)

        req.referer?.let { builder.header("Referer", it) }
        req.headers.forEach { (k, v) -> builder.header(k, v) }

        client.newCall(builder.build()).execute().use { resp ->
            Resp(
                code = resp.code,
                body = resp.body?.string().orEmpty(),
                contentType = resp.header("Content-Type"),
            )
        }
    }

    companion object {
        const val UA = "SMUPocket/0.1 (Android)"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * 把 [SessionStore] 伪装成 OkHttp 的 CookieJar。
 * 只要挂到 OkHttpClient 上，Retrofit 的每个请求都会自动带 cookie、并自动回收 Set-Cookie。
 * internal 是为了让 core/JwClient.kt 复用同一份实现。
 */
internal class SessionCookieJar(private val session: SessionStore) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        session.loadCookies(url.toString())

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        session.saveCookies(url.host, cookies)
    }
}

package com.smu.pocket.core

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 登录态（cookie）存储。
 *
 * 关键设计：
 * - **密码永不出现在这里**。密码只在学校登录页（WebView）里输入，
 *   我们只接管登录成功后的 cookie。这样验证码、密码前端加密、CAS 多跳、
 *   微信扫码全部免费获得。
 * - 按 host 分别存，教务和电费互不干扰。
 * - Cookie 只通过 Keystore + AES-GCM 加密保存，不保存旧版明文。
 */
class SessionStore(context: Context) {

    private val storage = EncryptedStore(context, "session")
    init {
        check(context.getSharedPreferences("smu_session", Context.MODE_PRIVATE).edit().clear().commit())
        check(context.deleteSharedPreferences("smu_session"))
    }
    private val lock = Any()
    private val cache: MutableMap<String, MutableMap<String, String>> = try {
        storage.read()?.let { Json.decodeFromString<Map<String, Map<String, String>>>(it) }
            ?.mapValues { it.value.toMutableMap() }?.toMutableMap() ?: mutableMapOf()
    } catch (e: Exception) {
        storage.clear()
        mutableMapOf()
    }

    private var acceptingResponses = cache.values.any { it.isNotEmpty() }

    /** 拼成 OkHttp 的 Cookie 请求头 */
    fun cookieHeader(url: String): String {
        val host = url.toHttpUrlOrNull()?.host ?: return ""
        return synchronized(lock) {
            table(host).entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }

    fun loadCookies(url: String): List<Cookie> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyList()
        val snapshot = synchronized(lock) { table(httpUrl.host).toMap() }
        return snapshot.mapNotNull { (name, value) -> Cookie.parse(httpUrl, "$name=$value") }
    }

    fun saveCookies(host: String, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            if (!acceptingResponses) return
            val t = table(host)
            cookies.forEach { t[it.name] = it.value }
            persist()
        }
    }

    /** WebView 登录成功后拿到的一整串 cookie，直接灌进来 */
    fun saveRawCookieHeader(url: String, header: String) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        if (header.isBlank()) return
        synchronized(lock) {
            acceptingResponses = true
            val t = table(host)
            header.split(';').forEach { pair ->
                val i = pair.indexOf('=')
                if (i > 0) t[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
            }
            persist()
        }
    }

    fun isLoggedIn(): Boolean {
        return synchronized(lock) { cache.values.any { it.isNotEmpty() } }
    }

    fun clear() {
        synchronized(lock) {
            acceptingResponses = false
            cache.clear()
            storage.clear()
        }
    }

    private fun table(host: String): MutableMap<String, String> =
        cache.getOrPut(host) { linkedMapOf() }

    private fun persist() {
        try {
            storage.write(Json.encodeToString(cache.mapValues { it.value.toMap() }))
        } catch (e: Exception) {
            acceptingResponses = false
            cache.clear()
            storage.clear()
            throw e
        }
    }
}

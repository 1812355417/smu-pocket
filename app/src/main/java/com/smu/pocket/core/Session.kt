package com.smu.pocket.core

import android.content.Context
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
 * - P1 用普通 SharedPreferences；P2 接真实 cookie 后换成
 *   EncryptedSharedPreferences（androidx.security.crypto），对外接口不变。
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val lock = Any()
    private val cache = mutableMapOf<String, MutableMap<String, String>>()

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
            val t = table(host)
            cookies.forEach { t[it.name] = it.value }
            persist(host, t)
        }
    }

    /** WebView 登录成功后拿到的一整串 cookie，直接灌进来 */
    fun saveRawCookieHeader(url: String, header: String) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        if (header.isBlank()) return
        synchronized(lock) {
            val t = table(host)
            header.split(';').forEach { pair ->
                val i = pair.indexOf('=')
                if (i > 0) t[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
            }
            persist(host, t)
        }
    }

    fun isLoggedIn(): Boolean {
        val keys = synchronized(lock) { prefs.all.keys.toList() }
        return keys.any { it.startsWith(KEY_PREFIX) }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
            prefs.edit().clear().apply()
        }
    }

    // 下面三个必须在 lock 内调用
    private fun table(host: String): MutableMap<String, String> = cache.getOrPut(host) {
        val raw = prefs.getString(KEY_PREFIX + host, "").orEmpty()
        val t = linkedMapOf<String, String>()
        raw.split(';').forEach { pair ->
            val i = pair.indexOf('=')
            if (i > 0) t[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
        }
        t
    }

    private fun persist(host: String, t: Map<String, String>) {
        val raw = t.entries.joinToString("; ") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_PREFIX + host, raw).apply()
    }

    private companion object {
        const val PREF = "smu_session"
        const val KEY_PREFIX = "h_"
    }
}

package com.smu.pocket.core

import android.webkit.CookieManager
import java.net.URI

/**
 * 从 WebView 里把 cookie 捞出来。
 *
 * 三个必须知道的点（踩过的坑都在这里）：
 *
 * 1. **不能用 JS 读 cookie**。教务的会话 cookie 基本都是 HttpOnly 的，
 *    `document.cookie` 看不见它。只能走 [CookieManager.getCookie]。
 *
 * 2. **cookie 是按 URL 记的，不是全局的**。CAS / 统一身份认证场景下
 *    `cas.xxx.edu.cn` 和 `zhjw.xxx.edu.cn` 各有一份，必须按 host 分别存，
 *    否则你只捞到了 CAS 的那份，回去请求教务就是未登录。
 *
 * 3. **捞之前要 flush()**。CookieManager 落盘是异步的，刚 Set-Cookie 完立刻
 *    getCookie 有可能拿到旧值（Android 5/6 尤其明显）。
 */
object CookieHarvester {

    fun flush() {
        CookieManager.getInstance().flush()
    }

    /**
     * 按给定的 URL 列表逐个捞 cookie，返回 (host → "a=1; b=2")。
     * 同一个 host 被多次命中时取更完整的那一份。
     */
    fun harvest(vararg urls: String): List<Pair<String, String>> {
        if (urls.isEmpty()) return emptyList()
        flush()

        val manager = CookieManager.getInstance()
        val byHost = LinkedHashMap<String, String>()

        for (url in urls) {
            if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) continue
            val host = hostOf(url) ?: continue
            val header = manager.getCookie(url)?.takeIf { it.isNotBlank() } ?: continue
            val old = byHost[host]
            if (old == null || header.length > old.length) byHost[host] = header
        }

        return byHost.map { (host, header) -> host to header }
    }

    fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (e: Exception) {
        null
    }
}

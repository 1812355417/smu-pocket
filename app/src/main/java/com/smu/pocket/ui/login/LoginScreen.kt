package com.smu.pocket.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.smu.pocket.core.CookieHarvester
import com.smu.pocket.data.JwConfig
import com.smu.pocket.ui.UiState
import kotlinx.coroutines.delay

private const val TAG = "JwLogin"
private const val POLL_INTERVAL_MS = 2_000L
private const val MAX_POLLS = 45          // 45 × 2s = 90 秒
private const val HINT_AFTER_POLLS = 15    // 30 秒后提示一下

/**
 * 登录页。
 *
 * 核心决定：**不在 App 里 POST 账号密码**，而是开学校自己的登录页让用户扫码。
 * 收益：极验滑块、密码前端加密、CAS 多跳全部免适配，我们只要结果（cookie）。
 *
 * 流程：
 * 1. WebView 打开教务首页（带扫码登录）
 * 2. 每 2 秒从 CookieManager 捞一次 cookie 存进 SessionStore
 * 3. 顺带发一个**轻量**请求（`zc=1`）探一次会话是否有效
 * 4. 探通了 → ViewModel 把 loggedIn 置 true → RootScreen 自动换到主页、WebView 被销毁
 */
@Composable
fun LoginScreen(
    state: UiState,
    onDemo: () -> Unit,
    onCookieHarvested: (host: String, cookieHeader: String) -> Unit,
    verifySession: suspend () -> Boolean,
    onLoginCancelled: () -> Unit = {},
) {
    var showWeb by rememberSaveable { mutableStateOf(false) }

    if (showWeb) {
        LoginWeb(
            onCookieHarvested = onCookieHarvested,
            verifySession = verifySession,
            onClose = {
                onLoginCancelled()
                showWeb = false
            },
        )
    } else {
        LoginIntro(state = state, onOpenWeb = { showWeb = true }, onDemo = onDemo)
    }
}

@Composable
private fun LoginIntro(
    state: UiState,
    onOpenWeb: () -> Unit,
    onDemo: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "南医大口袋", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "非官方客户端 · 课表 / 成绩",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "登录在学校自己的页面里完成，App 不接触密码、不保存密码。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点下面的按钮打开教务首页，用手机扫码登录；" +
                "登录成功后会自动跳回来，不需要手动关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
            Text("打开教务系统登录（扫码）")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
            Text("先用演示数据看看")
        }

        if (state.loading) {
            Spacer(Modifier.height(16.dp))
            Text(text = "正在加载…", style = MaterialTheme.typography.bodySmall)
        }

        state.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWeb(
    onCookieHarvested: (host: String, cookieHeader: String) -> Unit,
    verifySession: suspend () -> Boolean,
    onClose: () -> Unit,
) {
    var currentUrl by remember { mutableStateOf(JwConfig.LOGIN_URL) }
    var hint by remember { mutableStateOf("扫码完成后会自动进入，无需手动操作") }
    var done by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // WebView 必须显式销毁，否则 Activity 反复进出会泄漏
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { w ->
                w.stopLoading()
                w.loadUrl("about:blank")
                w.destroy()
            }
            webView = null
        }
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "开始轮询，间隔 ${POLL_INTERVAL_MS}ms，最多 ${MAX_POLLS} 次")
        var polls = 0

        while (polls < MAX_POLLS) {
            delay(POLL_INTERVAL_MS)
            polls++

            // 同时捞当前页和登录页：CAS 场景下凭证可能落在另一个 host 上
            val harvested = CookieHarvester.harvest(currentUrl, JwConfig.LOGIN_URL)
            if (harvested.isEmpty()) {
                if (polls == HINT_AFTER_POLLS) hint = "还没拿到 cookie，确认页面是否加载完了"
                continue
            }

            harvested.forEach { (host, header) ->
                Log.d(TAG, "捞到 cookie host=$host len=${header.length}")
                onCookieHarvested(host, header)
            }

            val ok = runCatching { verifySession() }.getOrDefault(false)
            Log.d(TAG, "第 $polls 次探测：${if (ok) "已登录" else "未登录"}")

            if (ok) {
                hint = "登录成功，正在加载课表…"
                done = true
                return@LaunchedEffect
            }

            if (polls == HINT_AFTER_POLLS) {
                hint = "还没检测到登录成功，请确认已经完成扫码"
            }
        }

        hint = "等了 90 秒仍未登录成功，可以点「关闭」重试"
        Log.w(TAG, "轮询超时")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "教务系统登录", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClose) { Text("关闭") }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    // 关键：CAS / 统一身份认证跨域回调时，第三方 cookie 必须开
                    cookies.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            currentUrl = url
                            Log.d(TAG, "onPageStarted: $url")
                        }

                        // SPA / history.pushState 也能抓到，比 onPageFinished 可靠
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            currentUrl = url
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            currentUrl = url
                            CookieHarvester.flush()
                            Log.d(TAG, "onPageFinished: $url")
                        }
                    }

                    // 扫码页依赖 JS 定时器 / 弹窗，没有 ChromeClient 会走不下去
                    webChromeClient = WebChromeClient()

                    loadUrl(JwConfig.LOGIN_URL)
                }.also { webView = it }
            },
        )
    }
}

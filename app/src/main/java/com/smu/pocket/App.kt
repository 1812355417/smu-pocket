package com.smu.pocket

import android.app.Application
import com.smu.pocket.core.CacheStore
import com.smu.pocket.core.TermStore
import com.smu.pocket.core.JwClient
import com.smu.pocket.core.OkHttpTransport
import com.smu.pocket.core.SessionStore
import com.smu.pocket.data.FakeJw
import com.smu.pocket.data.JwApi
import com.smu.pocket.data.JwConfig
import com.smu.pocket.data.JwService
import com.smu.pocket.data.RealJw
import com.smu.pocket.data.TermResolver

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.smu.pocket.core.CookieHarvester.clearWebLoginData()
        container = AppContainer(this)
    }
}

/**
 * 手写依赖容器。**故意不用 Hilt/Koin**：整个 App 就两三个对象。
 */
class AppContainer(app: Application) {

    val session: SessionStore = SessionStore(app)
    val cache: CacheStore = CacheStore(app)
    val termStore: TermStore = TermStore(app)
    /** 学期代码解析：教务页面 → 本地缓存 → 日期推算 → 常量 */
    private val termResolver: TermResolver by lazy { TermResolver(termStore) }

    @Suppress("unused")
    val transport = OkHttpTransport(session)

    private val jwApi: JwApi by lazy {
        JwApi.create(JwClient.retrofit(session, JwConfig.BASE_URL))
    }

    /**
     * 有会话就用真接口，没有就退回演示数据。
     *
     * 用**函数**而不是 `val`：写成 `val` 会在 App 启动那一刻定死，
     * 扫码登录完之后还一直在用启动时选中的实现 —— 很隐蔽的坑。
     */
    fun jwService(): JwService =
        if (session.isLoggedIn()) RealJw(jwApi, termResolver) else FakeJw()
}

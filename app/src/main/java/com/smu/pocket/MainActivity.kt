package com.smu.pocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smu.pocket.core.CacheStore
import com.smu.pocket.core.SessionStore
import com.smu.pocket.data.JwService
import com.smu.pocket.ui.AppViewModel
import com.smu.pocket.ui.RootScreen
import com.smu.pocket.ui.theme.SmuPocketTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as App).container

        setContent {
            SmuPocketTheme {
                val vm: AppViewModel = viewModel(
                    factory = AppVmFactory(container::jwService, container.session, container.cache),
                )
                RootScreen(vm)
            }
        }
    }
}

/** 就两个依赖，写个 Factory 比引一个 DI 框架便宜 */
private class AppVmFactory(
    private val jwProvider: () -> JwService,
    private val session: SessionStore,
    private val cache: CacheStore,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(jwProvider, session, cache) as T
    }
}

package com.smu.pocket.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smu.pocket.ui.grades.GradesScreen
import com.smu.pocket.ui.login.LoginScreen
import com.smu.pocket.ui.schedule.ScheduleScreen
import com.smu.pocket.ui.settings.SettingsScreen
import com.smu.pocket.ui.today.TodayScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("今日", Icons.Outlined.WbSunny),
    Schedule("课表", Icons.Outlined.CalendarMonth),
    Grades("成绩", Icons.Outlined.Grade),
    Settings("设置", Icons.Outlined.Settings),
}

@Composable
fun RootScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (!state.loggedIn) {
        LoginScreen(
            state = state,
            onDemo = vm::enterDemo,
            onCookieHarvested = vm::onLoginCookies,
            verifySession = vm::verifySession,
            onLoginCancelled = vm::onLoginCancelled,
        )
        return
    }

    var tabName by rememberSaveable { mutableStateOf(Tab.Today.name) }
    val tab = Tab.entries.firstOrNull { it.name == tabName } ?: Tab.Today

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tabName = t.name },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        // 主界面保持绝对干净：没有任何提示条。
        // 缓存/失效状态只在「设置」里体现，并提供「同步最新课表与成绩」入口。
        val contentModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()

        when (tab) {
            Tab.Today -> TodayScreen(state = state, modifier = contentModifier)

            Tab.Schedule -> ScheduleScreen(
                state = state,
                modifier = contentModifier,
                onSelectWeek = vm::selectWeek,
                onPrevWeek = vm::prevWeek,
                onNextWeek = vm::nextWeek,
            )

            Tab.Grades -> GradesScreen(state = state, modifier = contentModifier)

            Tab.Settings -> SettingsScreen(
                state = state,
                modifier = contentModifier,
                onSync = vm::syncNow,
                onLogout = vm::logout,
            )
        }
    }
}

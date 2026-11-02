package com.smu.pocket.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smu.pocket.core.humanizeAgo
import com.smu.pocket.ui.UiState
import com.smu.pocket.ui.components.CardBox
import com.smu.pocket.ui.components.LabelValue
import com.smu.pocket.ui.components.ScreenHeader

@Composable
fun SettingsScreen(
    state: UiState,
    modifier: Modifier = Modifier,
    onSync: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "设置",
            subtitle = "非官方客户端 · 数据直连学校系统 · 不经过第三方服务器",
        )

        // ---- 同步入口 ----
        CardBox {
            Button(
                onClick = onSync,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("同步中…")
                } else {
                    Text("同步最新课表与成绩")
                }
            }

            Spacer(Modifier.height(8.dp))

            val tail = "如发现课程有变动或无法加载，请重新扫码登录。"
            Text(
                text = state.cachedAt?.let { "当前数据更新于 ${humanizeAgo(it)}，$tail" } ?: tail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- 接入状态 ----
        CardBox {
            Text(text = "接入状态", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            LabelValue(
                "教务系统",
                when {
                    state.demoMode -> "演示数据（未接入）"
                    state.stale -> "已接入 · 当前显示缓存"
                    else -> "已接入"
                },
            )
            LabelValue("当前学期", state.config?.termName ?: "—")
            LabelValue("学期代码", state.semesterCode ?: "—（演示数据）")
            LabelValue("第一周周一", state.config?.firstMonday?.toString() ?: "—")
            LabelValue("总周数", state.config?.totalWeeks?.toString() ?: "—")
            LabelValue("课表数据", "${state.scheduleCourses.size} 条")
            LabelValue("成绩数据", "${state.grades.size} 条")
        }

        // ---- 退出登录 ----
        CardBox {
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("退出登录（清空登录态）")
            }
            Text(
                text = "退出后会同时清空本地缓存，下次需要重新扫码。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- 关于 ----
        CardBox {
            Text(text = "关于", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "南医大口袋 v0.1.0",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "账号密码只在学校的登录页输入，App 不保存密码、不上传任何数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "本项目仅供学习交流与个人使用，请遵守学校信息系统使用规定，不要高频请求。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

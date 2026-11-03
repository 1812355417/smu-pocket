package com.smu.pocket.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smu.pocket.R
import com.smu.pocket.core.humanizeAgo
import com.smu.pocket.ui.UiState
import com.smu.pocket.ui.components.CardBox
import com.smu.pocket.ui.components.LabelValue
import com.smu.pocket.ui.components.ScreenHeader

/** 更新通知 / 问题反馈 QQ 群。点一下复制到剪贴板 */
private const val QQ_GROUP = "1128162850"

/** App 版本号。改版本时和 app/build.gradle.kts 的 versionName 一起改 */
private const val APP_VERSION = "v1.1.1"

@Composable
fun SettingsScreen(
    state: UiState,
    modifier: Modifier = Modifier,
    onSync: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showSupport by remember { mutableStateOf(false) }

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
            // 只有真的在演示模式才敢标「演示数据」：缓存恢复时 semesterCode 是空的，
            // 那时显示「—（演示数据）」会让用户以为课表是假数据
            LabelValue("学期代码", state.semesterCode ?: if (state.demoMode) "—（演示数据）" else "—")
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
                text = "南医大口袋 $APP_VERSION",
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

        // ---- 开发者与反馈 ----
        CardBox {
            Text(
                text = "开发者：QIU",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            // 群号做成可点击复制的样式：整行可点、号码带下划线，点完 Toast 提示
            Row(
                modifier = Modifier
                    .clickable {
                        clipboard.setText(AnnotatedString(QQ_GROUP))
                        Toast.makeText(context, "QQ群号已复制", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "更新通知 / 问题反馈 QQ 群：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = QQ_GROUP,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.Underline,
                )
            }

            Spacer(Modifier.height(4.dp))

            // 低调的灰色文本按钮：点开赞赏码
            TextButton(
                onClick = { showSupport = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("☕ 赞赏与支持", style = MaterialTheme.typography.labelMedium)
            }
        }

        // ---- 版本 / 开源 ----
        Text(
            text = "南医大口袋 $APP_VERSION · 遵循 GPL-3.0 开源协议",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
        )
    }

    if (showSupport) {
        SupportDialog(onClose = { showSupport = false })
    }
}

/** 赞赏弹窗：文案 + 微信收款码 + 关闭按钮（纯自愿，和学校无关） */
@Composable
private fun SupportDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("支持开发者") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "感谢使用南医大口袋！如果觉得这个 App 帮到了你，可以支持一下。" +
                        "纯属自愿，与学校无关。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                val painter = painterResource(R.drawable.wechat_pay)
                // 按图片真实宽高比算高度，避免不同机型上被拉扁或裁切
                val intrinsic = painter.intrinsicSize
                val ratio = if (intrinsic.width > 0f && intrinsic.height > 0f) {
                    intrinsic.height / intrinsic.width
                } else {
                    1f
                }
                Image(
                    painter = painter,
                    contentDescription = "微信收款码",
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(ratio),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}

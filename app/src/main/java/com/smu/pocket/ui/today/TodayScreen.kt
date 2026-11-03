package com.smu.pocket.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.smu.pocket.core.Course
import com.smu.pocket.core.Period
import com.smu.pocket.core.TimetableConfig
import com.smu.pocket.ui.UiState
import com.smu.pocket.ui.components.CardBox
import com.smu.pocket.ui.components.SelfStudyChip
import java.time.LocalDate

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun TodayScreen(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 直接把日期作为页面主标题（原来的问候语已去掉）
        Text(
            text = "$today · 星期${cn(today.dayOfWeek.value)} · 第 ${state.currentWeek} 周",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )

        CardBox {
            Text(
                text = "今日课程 ${state.todayCourses.size} 节",
                style = MaterialTheme.typography.titleSmall,
            )

            Spacer(Modifier.height(6.dp))

            if (!state.todayDataReady) {
                Text(
                    text = "课表还没更新到本周（当前是第 ${state.currentWeek} 周）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.todayCourses.isEmpty()) {
                Text(
                    text = "今天没有课。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.todayCourses.forEachIndexed { index, course ->
                    if (index > 0) {
                        Spacer(Modifier.height(10.dp))
                    }
                    CourseRow(course, state.config)
                }
            }
        }

        // ---- 明日课程 ----
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "明日课程",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tomorrowLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))

            when {
                // 今天是周日 → 明天属于下一周，数据可能还没拉回来
                !state.tomorrowDataReady -> Text(
                    text = "明天是第 ${state.tomorrowWeek ?: 0} 周，那周的课表还没加载",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.tomorrowCourses.isEmpty() -> Text(
                    text = tomorrowEmptyHint(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.tomorrowCourses.forEachIndexed { index, course ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    CourseRow(course, state.config)
                }
            }
        }
        if (state.weekTotal > 0) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本周概览",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "共 ${state.weekTotal} 节",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                WeekBars(counts = state.dailyCounts, todayIndex = today.dayOfWeek.value)
            }
        }

        // ---- 考试安排 ----
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "考试安排",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.examNumbersReady && state.upcomingExams.isNotEmpty()) {
                    Text(
                        text = "共 ${state.upcomingExams.size} 场",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            when {
                // 网考号还没回来：整张考试列表先别画。
                // 否则列表先渲染、号码过一秒再「跳」出来，看着像闪烁/卡顿。
                !state.examNumbersReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在加载考试安排…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.upcomingExams.isEmpty() -> Text(
                    text = "近期暂无考试安排",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 全部显示，**不截断**：之前这里写死了 take(6)，
                // 第 7 场之后的考试（正好是 1 月那几场）被裁掉，看着像「只到 12 月」
                else -> state.upcomingExams.forEachIndexed { index, exam ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    ExamRow(exam, state.examNumbers)
                }
            }
        }
        if (state.loading) {
            CardBox {
                Text(
                    text = "正在加载…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.error?.let { err ->
            CardBox {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}


@Composable
private fun ExamRow(exam: Course, examNumbers: Map<String, String>) {
    val date = exam.date ?: return
    // 课程名 -> 网考号；没有就只显示课名，不留空括号
    // （课名两边偶尔带空格，精确匹配不上时再用 trim 过的试一次）
    val examNo = (examNumbers[exam.name] ?: examNumbers[exam.name.trim()])
        ?.takeIf { it.isNotBlank() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "周${cn(date.dayOfWeek.value)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(Modifier.weight(1f)) {
            // 网考号不强调：颜色/字号跟下面「地点 · 时间」那行保持一致（灰色小字）
            val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
            val secondarySize = MaterialTheme.typography.labelSmall.fontSize
            // 用 AnnotatedString 而不是并排两个 Text：课名很长时会自然换行，
            // 号码跟着课名一起折行，不会把课名挤成省略号
            Text(
                text = buildAnnotatedString {
                    append(exam.name)
                    examNo?.let {
                        withStyle(
                            SpanStyle(
                                color = secondaryColor,
                                fontSize = secondarySize,
                                // 课名是 Medium，这里必须显式 Normal，否则会继承成粗体
                                fontWeight = FontWeight.Normal,
                            ),
                        ) {
                            append(" [网考号：$it]")
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val where = listOfNotNull(
                exam.room?.takeIf { it.isNotBlank() } ?: "地点待定",
                exam.timeLabel,
            ).joinToString(" · ")
            Text(
                text = where,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CourseRow(course: Course, config: TimetableConfig?) {
    // 时间轴：优先用教务给的 qssj/jssj（最准），拿不到就退回本地作息表（含 11~14 节），
    // 再退到「第 N 节」。三层兜底，晚课不会因为缺时刻而显示成空白。
    val label = course.timeLabel
    val startText = label?.substringBefore("-")?.takeIf { it.isNotBlank() }
        ?: periodAt(config, course.startSection)?.start
        ?: "第${course.startSection}节"
    val endText = label?.substringAfter("-", "")?.ifBlank { null }
        ?: periodAt(config, course.endSection)?.end
        ?: "第${course.endSection}节"

    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.width(64.dp)) {
            Text(
                text = startText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = endText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (course.isSelfStudy) {
                    Spacer(Modifier.width(6.dp))
                    SelfStudyChip()
                }
            }
            val sub = listOfNotNull(
                // 自主学习不用去教室，别显示一个空教室占位
                if (course.isSelfStudy) "无需前往教室" else course.room,
                shortTeachers(course.teacher),
                course.content,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 每天几节课的柱状概览，今天高亮 */
@Composable
private fun WeekBars(counts: List<Int>, todayIndex: Int) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        counts.forEachIndexed { index, count ->
            val isToday = index + 1 == todayIndex
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))

                val ratio = count.toFloat() / max
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((56.dp * ratio).coerceAtLeast(if (count == 0) 3.dp else 8.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        ),
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = DAY_LABELS[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 本地作息表查一节的时间；11~14 节也在表里，所以晚课同样能查到 */
private fun periodAt(config: TimetableConfig?, section: Int): Period? =
    config?.periods?.firstOrNull { it.index == section }

/** 教务把 12 个老师的名字拼在一个字段里，卡片上只显示前 3 个 */
private fun shortTeachers(raw: String?): String? {
    val names = raw?.split(',', '，', '、')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
    if (names.isEmpty()) return null
    return if (names.size <= 3) names.joinToString("、")
    else names.take(3).joinToString("、") + " 等${names.size}人"
}

private fun tomorrowDate(): LocalDate = LocalDate.now().plusDays(1)

private fun tomorrowLabel(): String {
    val d = tomorrowDate()
    return "明天 · ${d.monthValue}月${d.dayOfMonth}日 星期${cn(d.dayOfWeek.value)}"
}

private fun tomorrowEmptyHint(): String = when (tomorrowDate().dayOfWeek.value) {
    6 -> "明天是周六，没有课，睡个懒觉吧"
    7 -> "明天是周日，没有课，好好休息"
    else -> "明天没有课，好好休息"
}

private fun cn(dayOfWeek: Int): String {
    val labels = "一二三四五六日"
    return if (dayOfWeek in 1..7) labels[dayOfWeek - 1].toString() else "?"
}

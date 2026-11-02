package com.smu.pocket.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smu.pocket.core.Course
import com.smu.pocket.core.TimetableConfig
import com.smu.pocket.ui.UiState
import com.smu.pocket.ui.components.EmptyHint
import com.smu.pocket.ui.components.SelfStudyChip
import com.smu.pocket.ui.components.ScreenHeader

/** 一节课的高度。跨节课程按 N 倍高度渲染，色块才能真正连成一块 */
private val CELL_HEIGHT: Dp = 62.dp
private val TIME_COL_WIDTH: Dp = 26.dp

@Composable
fun ScheduleScreen(
    state: UiState,
    modifier: Modifier = Modifier,
    onSelectWeek: (Int) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val config = state.config

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "第 ${state.selectedWeek} 周",
            subtitle = config?.let {
                "${it.termName} · 共 ${it.totalWeeks} 周 · 该周 ${state.selectedWeekTotal} 节"
            } ?: "尚未连接到教务系统",
        )

        if (config == null) {
            EmptyHint("还没有课表数据")
            return@Column
        }

        WeekStrip(
            totalWeeks = config.totalWeeks,
            currentWeek = state.currentWeek,
            selectedWeek = state.selectedWeek,
            onSelect = onSelectWeek,
        )

        Spacer(Modifier.height(8.dp))
        DayHeader()
        TimetableGrid(state = state, config = config, modifier = Modifier.weight(1f))
        WeekNav(onPrev = onPrevWeek, onNext = onNextWeek)
    }
}

@Composable
private fun WeekStrip(
    totalWeeks: Int,
    currentWeek: Int,
    selectedWeek: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // 选中的周自动滚到可见位置（否则第 9 周会藏在屏幕右边）
    LaunchedEffect(selectedWeek) {
        if (selectedWeek in 1..totalWeeks) {
            listState.animateScrollToItem((selectedWeek - 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(totalWeeks) { index ->
            val week = index + 1
            FilterChip(
                selected = week == selectedWeek,
                onClick = { onSelect(week) },
                label = { Text(if (week == currentWeek) "$week·今" else "$week") },
            )
        }
    }
}

@Composable
private fun DayHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Spacer(Modifier.width(TIME_COL_WIDTH))
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 课表网格。
 *
 * 每天一个独立的 Column：跨节课程渲染成 `span * CELL_HEIGHT` 高的色块，
 * 并跳过它覆盖的后续几节 —— 4 节课的课程是一整块，而不是「一格有色 + 三格空白」。
 *
 * 颜色用教务自己给的 `bgcolor`（每门课一种），比统一配色更容易一眼分辨。
 */
@Composable
private fun TimetableGrid(
    state: UiState,
    config: TimetableConfig,
    modifier: Modifier = Modifier,
) {
    val week = state.selectedWeek
    val courses = state.scheduleCourses.filter { it.occursIn(week) }
    val byDay = courses.groupBy { it.dayOfWeek }
    val sections = config.periods.map { it.index }

    Row(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.width(TIME_COL_WIDTH)) {
            sections.forEach { index ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(CELL_HEIGHT)
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        for (day in 1..7) {
            val dayCourses = byDay[day].orEmpty()
            val starts = dayCourses.associateBy { it.startSection }
            val covered = dayCourses.map { it.startSection..it.endSection }

            Column(Modifier.weight(1f)) {
                var i = 0
                while (i < sections.size) {
                    val section = sections[i]
                    val course = starts[section]

                    when {
                        course != null -> {
                            val span = (course.endSection - course.startSection + 1)
                                .coerceIn(1, sections.size - i)
                            CourseCell(course, CELL_HEIGHT * span)
                            i += span
                        }

                        covered.any { section in it } -> i++

                        else -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(CELL_HEIGHT)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    ),
                            )
                            i++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCell(course: Course, height: Dp) {
    val base = parseCourseColor(course.colorHex)
    val container = base ?: MaterialTheme.colorScheme.primary
    // 亮色块配深字，深色块配白字 —— 教务给的配色有深有浅，不能写死
    val content = if (container.luminance() > 0.55f) Color(0xFF1B1B1B) else Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(1.dp),
        shape = RoundedCornerShape(6.dp),
        color = container.copy(alpha = 0.88f),
        contentColor = content,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(3.dp),
        ) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.isSelfStudy) {
                // 格子只有 ~45dp 宽，「自主学习」四个字放不下，用「自主」
                SelfStudyChip(compact = true, modifier = Modifier.padding(top = 2.dp))
            }
            val sub = course.room ?: course.teacher
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = content.copy(alpha = 0.85f),
                )
            }
            course.content?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = content.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** 把教务工的 "#1f86c7" 解析成 Compose Color；解析不了就用主题色 */
private fun parseCourseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

@Composable
private fun WeekNav(onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrev) { Text("← 上一周") }
        TextButton(onClick = onNext) { Text("下一周 →") }
    }
}

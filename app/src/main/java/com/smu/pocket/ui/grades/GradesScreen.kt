package com.smu.pocket.ui.grades

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smu.pocket.core.Grade
import com.smu.pocket.ui.UiState
import com.smu.pocket.ui.components.CardBox
import com.smu.pocket.ui.components.EmptyHint
import com.smu.pocket.ui.components.ScreenHeader
import java.util.Locale

/**
 * 成绩页。
 *
 * 结构：**LazyColumn + 按学期折叠**。
 * 用 `xnxqmc`（领域模型里的 `Grade.term`）分组，每个学期一个可点击 Header，
 * 展开/收起用 AnimatedVisibility 做高度动画。
 *
 * 两个实现细节：
 * 1. 每个学期的内容**始终留在组合里**，只切换 `visible` ——
 *    如果写成 `if (expanded) item { ... }`，条目是被重新创建出来的，就没有展开动画了。
 * 2. 默认展开**最新的那个学期**，其余收起。用一个可空的 Set 表示：
 *    `null` = 还没动过（用默认值），非 null = 用户已经手动点过。
 */
@Composable
fun GradesScreen(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    val terms = state.gradesByTerm

    if (terms.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            ScreenHeader(title = "成绩", subtitle = "还没有成绩数据")
            EmptyHint("还没有成绩数据")
        }
        return
    }

    // 默认**全部收起**，用户点了哪个就展开哪个
    var expandedTerms by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun toggle(term: String) {
        expandedTerms = if (term in expandedTerms) expandedTerms - term else expandedTerms + term
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "title") {
            ScreenHeader(
                title = "成绩",
                subtitle = "共 ${state.grades.size} 门 · ${terms.size} 个学期 · 总学分 ${fmt(state.totalCredit, 1)}",
            )
        }

        item(key = "summary") {
            SummaryCard(state)
        }

        terms.forEach { (term, list) ->
            item(key = "header_$term") {
                TermHeader(
                    term = term,
                    list = list,
                    expanded = term in expandedTerms,
                    onClick = { toggle(term) },
                )
            }

            item(key = "body_$term") {
                AnimatedVisibility(
                    visible = term in expandedTerms,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        list.forEach { grade -> GradeRow(grade) }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    CardBox {
        Row(Modifier.fillMaxWidth()) {
            Stat("加权绩点", fmt(state.weightedGpa, 2), Modifier.weight(1f))
            Stat("加权平均", fmt(state.weightedScore, 1), Modifier.weight(1f))
            Stat("总学分", fmt(state.totalCredit, 1), Modifier.weight(1f))
            Stat("门数", state.grades.size.toString(), Modifier.weight(1f))
        }
        if (state.grades.any { it.score == null }) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "含等级制课程（没有百分制分数），加权平均分只统计有分数的课程。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 可点击的学期头：显示门数、均分，右侧箭头随展开状态旋转 */
@Composable
private fun TermHeader(
    term: String,
    list: List<Grade>,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "termArrow")
    val avg = list.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = term,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (avg != null) "${list.size} 门 · 均分 ${fmt(avg, 1)}"
                    else "${list.size} 门",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GradeRow(grade: Grade) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = grade.course,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            grade.kind?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = grade.level ?: fmt(grade.score, 0),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            text = "${fmt(grade.credit, 1)} 学分",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** null 统一显示成「—」，绝不显示 0 —— 0 分和「没有这项」是两件事 */
private fun fmt(value: Double?, digits: Int): String =
    if (value == null) "—" else String.format(Locale.US, "%.${digits}f", value)

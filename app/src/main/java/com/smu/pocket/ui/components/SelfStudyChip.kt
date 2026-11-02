package com.smu.pocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 浅绿系，和普通线下课的蓝/橙/红区分开 */
private val SelfStudyGreen = Color(0xFF2E7D32)

/**
 * 「自主学习」标签。
 *
 * 数据来源：教务 `jxhjmc`（教学环节名称）= "自主学习"。
 * 实测 118 行样本里 19 行是它，且**全部没有教室** —— 所以这条规则很干净，不会误伤。
 *
 * @param compact 课表网格里格子只有 ~45dp 宽，放不下四个字，用「自主」
 */
@Composable
fun SelfStudyChip(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (compact) "自主" else "自主学习",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = SelfStudyGreen,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SelfStudyGreen.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

package com.smu.pocket

import com.smu.pocket.core.CachedConfig
import com.smu.pocket.core.CachedPeriod
import com.smu.pocket.core.CachedSnapshot
import com.smu.pocket.core.DefaultPeriods
import com.smu.pocket.core.TOTAL_SECTIONS
import com.smu.pocket.core.toConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表布局 / 作息表的回归测试。
 *
 * 起因：晚课排到 11~14 节，但网格只按 `config.periods` 画了 10 行，
 * 这些课整块消失在网格外；而**旧版本缓存里存的就是 10 条作息**，
 * 所以升级后不清缓存，也得能正确渲染。
 */
class TimetableLayoutTest {

    @Test
    fun defaultPeriodsCoverAllSections() {
        assertEquals(TOTAL_SECTIONS, DefaultPeriods.ALL.size)
        assertEquals((1..TOTAL_SECTIONS).toList(), DefaultPeriods.ALL.map { it.index })
        // 每节都得有起止时刻：「今日」页在拿不到 qssj/jssj 时要靠它兜底
        assertTrue(DefaultPeriods.ALL.all { it.start.isNotBlank() && it.end.isNotBlank() })
    }

    @Test
    fun oldCacheWithTenPeriodsIsBackfilled() {
        val legacy = CachedSnapshot(
            config = CachedConfig(
                termName = "2026-2027 学年 第 1 学期",
                firstMonday = "2026-08-31",
                totalWeeks = 20,
                periods = (1..10).map { CachedPeriod(it, "08:00", "08:45") },
            ),
        )

        val config = legacy.toConfig()!!
        assertEquals("旧缓存必须补到 14 节", TOTAL_SECTIONS, config.periods.size)
        assertTrue("11~14 节要补出来", config.periods.any { it.index == 14 })
    }

    @Test
    fun cacheWithFullPeriodsKeepsItsOwnTimes() {
        val fresh = CachedSnapshot(
            config = CachedConfig(
                termName = "2026-2027 学年 第 1 学期",
                firstMonday = "2026-08-31",
                totalWeeks = 20,
                periods = (1..TOTAL_SECTIONS).map { CachedPeriod(it, "07:07", "07:52") },
            ),
        )

        val config = fresh.toConfig()!!
        assertEquals(TOTAL_SECTIONS, config.periods.size)
        // 已经够 14 条时不能被默认表覆盖（否则用户手改的作息会被悄悄改掉）
        assertEquals("07:07", config.periods.first().start)
    }
}

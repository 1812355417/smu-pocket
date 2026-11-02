package com.smu.pocket.data

import android.util.Log
import com.smu.pocket.core.TermCode
import com.smu.pocket.core.TermStore

/**
 * 解析「当前学期代码」。
 *
 * ## 结论：这个教务系统拿不到「当前学期」
 *
 * 实测过的所有途径：
 *
 * | 途径 | 结果 |
 * |---|---|
 * | `POST /new/student/xsgrkb/getXnxqList` 等 6 个 JSON 接口 | 全部 404 |
 * | `GET /new/student/xskccj/kccjList.page` | 返回 HTML，`<select id='xnxqdm'>` 里确实有学期列表 |
 *
 * 但那个下拉是**全量列表**（实测 202001 ~ 203402，最新在前），
 * **没有任何 `selected` 标记** —— 当前学期是 JS 运行时选的，静态 HTML 里看不出来。
 *
 * 早期实现「取最大六位数」直接把 203402 当成了当前学期，
 * 导致所有请求用错 `xnxqdm`、**全部返回空数据**。这条路走不通。
 *
 * ## 所以用日期推算
 *
 * 学年 9 月开学、2 月开学，边界固定，推算结果与教务一致，
 * 而且**不依赖网络、不会因为页面改版而失效**：
 *
 * ```
 * 9~12 月 → 当年第 1 学期（202601）
 * 1 月    → 上一年第 1 学期（202601）
 * 2~8 月  → 上一年第 2 学期（202602）
 * ```
 *
 * 这同样满足「新学期不用改代码、不用重新打包」的目标。
 */
class TermResolver(private val store: TermStore) {

    enum class Source { CACHE, DATE, FALLBACK }

    data class Resolved(val code: String, val source: Source)

    fun resolve(): Resolved {
        store.load()?.let {
            Log.d(TAG, "学期代码=$it 来源=CACHE")
            return Resolved(it, Source.CACHE)
        }

        val byDate = TermCode.fromDate()
        if (TermCode.isPlausible(byDate)) {
            store.save(byDate, "date")
            Log.d(TAG, "学期代码=$byDate 来源=DATE（按日期推算）")
            return Resolved(byDate, Source.DATE)
        }

        Log.w(TAG, "日期推算异常，使用兜底常量 ${TermCode.FALLBACK}")
        return Resolved(TermCode.FALLBACK, Source.FALLBACK)
    }

    /** 不联网的「当前学期」，探针用 */
    fun peek(): String = store.load() ?: TermCode.fromDate()

    private companion object {
        const val TAG = "TermResolver"
    }
}
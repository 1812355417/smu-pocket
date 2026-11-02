package com.smu.pocket.data

import com.smu.pocket.core.TermCode
import com.smu.pocket.core.TermStore
import java.time.LocalDate

/**
 * 解析「当前学期代码」。
 *
 * ## 为什么不是「先调学期接口」
 *
 * 这个教务系统**没有**查询当前学期的 JSON 接口。实测结论：
 *
 * | 候选路径 | 结果 |
 * |---|---|
 * | `POST /new/student/xsgrkb/getXnxqList` | 404 |
 * | `POST /new/common/getXnxq` | 404 |
 * | `POST /new/calendar/getCalendar` | 404 |
 * | `GET  /new/student/xskccj/kccjList.page` | 200，但**未登录时返回的就是登录页**，没有学期下拉 |
 *
 * 所以改成「能拿到就用，拿不到就推算」的四级链，每一级都向前兜底：
 *
 * ```
 * ① 教务页面 HTML 里的学期下拉（需要已登录）
 * ② 本地缓存（上次成功解析到的）
 * ③ 按日期推算（无网络也能工作，实测与教务一致）
 * ④ 常量 202601
 * ```
 *
 * 第 ③ 级是真正解决问题的那一级：学期边界固定（9 月开学），
 * 所以**以后新学期不需要改代码、不需要重新打包**。
 */
class TermResolver(
    private val api: JwApi,
    private val store: TermStore,
) {

    enum class Source { WEB, CACHE, DATE, FALLBACK }

    data class Resolved(val code: String, val source: Source)

    /** 完整解析：可能发一次网络请求（只有第 ① 级会） */
    suspend fun resolve(): Resolved {
        fromWeb()?.let {
            store.save(it, "web")
            return Resolved(it, Source.WEB)
        }

        store.load()?.let { return Resolved(it, Source.CACHE) }

        val byDate = TermCode.fromDate()
        if (TermCode.isValid(byDate)) {
            store.save(byDate, "date")
            return Resolved(byDate, Source.DATE)
        }

        return Resolved(TermCode.FALLBACK, Source.FALLBACK)
    }

    /**
     * 不联网的「当前学期」。
     *
     * 给扫码轮询的探针用 —— 那个链路每 2 秒打一次，绝不能因为它去抓页面。
     */
    fun peek(): String = store.load() ?: TermCode.fromDate()

    // ------------------------------------------------------------------

    private suspend fun fromWeb(): String? = runCatching {
        val response = api.termPage()
        if (!response.isSuccessful) return@runCatching null
        val html = response.body()?.use { it.string() }.orEmpty()
        parseTerm(html)
    }.getOrNull()

    companion object {

        /**
         * 从 HTML 里挑学期代码。
         *
         * 强智的学期下拉通常是：
         * ```html
         * <select id="xnxqdm">
         *   <option value="202601" selected>2026-2027学年第一学期</option>
         * </select>
         * ```
         *
         * 优先取带 `selected` 的那一项；没有标记就取**最大的六位码**（学期码单调递增）。
         * 登录页、错误页里没有 `<option>`，自然返回 null，落到下一级兜底。
         */
        fun parseTerm(html: String): String? {
            if (html.isBlank()) return null

            Regex("""(?is)<option[^>]*value=["'](\d{6})["'][^>]*?\bselected""")
                .find(html)?.let { return it.groupValues[1] }

            // 属性顺序可能是 selected 在前
            Regex("""(?is)<option[^>]*?\bselected[^>]*?value=["'](\d{6})["']""")
                .find(html)?.let { return it.groupValues[1] }

            return Regex("""(?i)<option[^>]*value=["'](\d{6})["']""")
                .findAll(html)
                .map { it.groupValues[1] }
                .maxOrNull()
        }
    }
}

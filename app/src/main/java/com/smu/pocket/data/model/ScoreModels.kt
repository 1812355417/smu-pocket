package com.smu.pocket.data.model

import kotlinx.serialization.Serializable

/**
 * 成绩响应。
 *
 * ⚠️ 实测结构是**两层**，不是你说的三层：
 * ```json
 * { "data": "", "rows": [ {...}, ... ], "total": 50 }
 * ```
 * - `data` 是**空字符串**，不是对象；
 * - `rows` 和 `data` 是**平级**的兄弟字段；
 * - `total` 是总条数(50)，而本次只返回了 20 条 → **必须分页**。
 *
 * 你描述的 `ScoreResponse → ScoreDataWrapper → ScoreData`（data.rows 嵌套）
 * 如果照着写，会得到 `rows = emptyList()` 且**不报错**——成绩页一片空白，
 * 但接口明明 200。这是最阴的一类 bug。
 *
 * 为兼容两种形态，[JwJson.scores] 同时认 `rows` 平级和 `data.rows` 嵌套。
 */
@Serializable
data class ScoreResponse(
    /** 业务状态码。0=成功；-401=未登录/会话失效（HTTP 仍然是 200！） */
    @Serializable(with = FlexibleIntSerializer::class)
    val code: Int = 0,
    val message: String = "",
    /** 实测为空字符串；某些实现会是对象，此时这里留空即可 */
    val data: String = "",
    val rows: List<ScoreData> = emptyList(),
    /** 服务器端总条数，用来判断还有没有下一页 */
    @Serializable(with = FlexibleIntSerializer::class)
    val total: Int = 0,
) {
    val hasMore: Boolean get() = rows.isNotEmpty() && rows.size < total
}

/**
 * 一条成绩。字段类型是照真实抓包定的，有几处和直觉相反：
 *
 * - `zcjfs` / `cjjd` / `xf` / `zxs` 实测是**数字**（88.2 / 3.8 / 3.0 / 48.0），
 *   但你贴的样例里是 `"88.20"` / `"3.00"` 字符串 → 用柔性字符串，两种都吃。
 * - `zcj`（总成绩）**本来就是字符串** `"88.20"`，和 `zcjfs` 存的是同一个值、不同精度。
 *   要数值就用 [score]，别直接拿 `zcj`。
 * - 学期字段是 `xnxqmc`（"2025-2026-2"）和 `xnxqdm`（"202502"），
 *   **不是** `xnxqdmc` —— 你样例里那个字段名在真实响应里不存在，会静默解析成 null。
 */
@Serializable
data class ScoreData(
    val kcmc: String? = null,
    val kcywmc: String? = null,
    val kcdm: String? = null,

    /** 学期代码 "202502" */
    val xnxqdm: String? = null,
    /** 学期名称 "2025-2026-2" */
    val xnxqmc: String? = null,

    /** 总成绩字符串 "88.20" */
    @Serializable(with = FlexibleStringSerializer::class)
    val zcj: String = "",
    /** 总成绩分数，实测为数字 88.2 */
    @Serializable(with = FlexibleStringSerializer::class)
    val zcjfs: String = "",
    /** 绩点，实测为数字 3.8 */
    @Serializable(with = FlexibleStringSerializer::class)
    val cjjd: String = "",
    /** 学分，实测为数字 3.0 */
    @Serializable(with = FlexibleStringSerializer::class)
    val xf: String = "",
    /** 总学时，实测为数字 48.0 */
    @Serializable(with = FlexibleStringSerializer::class)
    val zxs: String = "",

    /** 课程大类名称，如「公共基础课」 */
    val kcdlmc: String? = null,
    /** 课程分类名称（实测多为空串） */
    val kcflmc: String? = null,
    /** 修读方式名称，如「必修」 */
    val xdfsmc: String? = null,
    /** 考试性质名称，如「正常考试」 */
    val ksxzmc: String? = null,
    /** 计分方式名称，如「百分制」 */
    val cjfsmc: String? = null,
    /** 开课部门名称 */
    val kkbmmc: String? = null,
    /** 备注 */
    val bz: String? = null,

    /** 是否重修取最高分（1/0） */
    val ismax: String? = null,
    /** 是否有效（1/0） */
    val isactive: String? = null,
) {
    /** 数值型成绩。优先 zcjfs（精度更规范），退回 zcj */
    val score: Double?
        get() = zcjfs.trim().toDoubleOrNull() ?: zcj.trim().toDoubleOrNull()

    /** 绩点。等级制课程可能没有 */
    val gradePoint: Double? get() = cjjd.trim().toDoubleOrNull()

    /** 学分 */
    val credit: Double get() = xf.trim().toDoubleOrNull() ?: 0.0

    val passed: Boolean get() = (score ?: 0.0) >= 60.0
}

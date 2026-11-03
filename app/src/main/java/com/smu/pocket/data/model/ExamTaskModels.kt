package com.smu.pocket.data.model

import kotlinx.serialization.Serializable

/**
 * 课程任务（考试）响应。`POST /new/student/xskcrw/skrwDatas`
 *
 * 结构与成绩接口一致：`rows` 与 `data` **平级**，`data` 是空字符串：
 * ```json
 * { "code": 0, "data": "", "total": 12, "rows": [ {...}, ... ] }
 * ```
 *
 * 我们只用它来拿「网考号」——`rows[].xkh`，按 `kcmc`（课程名）对到考试上。
 * 兼容两种嵌套形态的逻辑在 [JwJson.examTasks]。
 */
@Serializable
data class ExamTaskResponse(
    /** 业务状态码。0=成功；-401=未登录/会话失效（HTTP 仍然是 200！） */
    @Serializable(with = FlexibleIntSerializer::class)
    val code: Int = 0,
    val message: String = "",
    val rows: List<ExamTaskData> = emptyList(),
    /** 服务器端总条数，用来判断还有没有下一页 */
    @Serializable(with = FlexibleIntSerializer::class)
    val total: Int = 0,
) {
    val ok: Boolean get() = code == 0

    val hasMore: Boolean get() = rows.isNotEmpty() && rows.size < total
}

/**
 * 一条课程任务（考试）。
 *
 * 字段名照**真实响应**（`skrwDatas` 实测）：
 * - `xid`：网考号，如 `"7667619"`。抓包时可能看到的是 `xkh`（部分页面/旧版本这么叫），
 *   所以两个字段都解析，[onlineExamNumber] 里 `xkh` 优先、退回 `xid`。
 * - 号码可能以数字返回（同一接口不同类型是这家的老毛病），用柔性字符串，两种都能吃。
 * - `kcmc`：课程名，UI 里用它和「考试安排」的课名对齐。
 * - `khfsmc`：考核方式，如「考试」。
 */
@Serializable
data class ExamTaskData(
    /** 抓包里的名字（部分响应/旧版本给这个） */
    @Serializable(with = FlexibleStringSerializer::class)
    val xkh: String = "",
    /** 实测真正的网考号字段 */
    @Serializable(with = FlexibleStringSerializer::class)
    val xid: String = "",
    val kcmc: String? = null,
    val khfsmc: String? = null,
) {
    /** 网考号：`xkh` 优先，没有就用 `xid` */
    val onlineExamNumber: String get() = xkh.trim().ifBlank { xid.trim() }
}

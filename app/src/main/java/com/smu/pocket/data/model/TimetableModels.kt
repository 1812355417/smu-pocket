package com.smu.pocket.data.model

import kotlinx.serialization.Serializable

/**
 * 课表响应。
 *
 * ⚠️ 实测真实响应是**对象**，不是你贴的那种裸数组：
 * ```json
 * { "code": 0, "message": "成功", "data": [ {...}, ... ] }
 * ```
 * 你贴的两条大概是 `data` 数组本身。为兼容两种形态，[JwJson.timetable] 里做了判断。
 */
@Serializable
data class TimetableResponse(
    @Serializable(with = FlexibleIntSerializer::class)
    val code: Int = 0,
    val message: String = "",
    val data: List<CourseData> = emptyList(),
) {
    val ok: Boolean get() = code == 0
}

/**
 * 一条课次。字段含义来自 118 行真实抓包的字段字典
 * （见 `smu-probe/字段字典-自动生成.md`）。
 *
 * 两条不同查询路径返回的字段**不一样**，模型必须能同时装下：
 *
 * | 字段 | 整学期总表（118 行） | 按周查询 |
 * |---|---|---|
 * | `zc` 周次 | `"12,13"` 字符串，含多周 | `8` 数字 |
 * | `qsrq` 真实日期 | ❌ 没有 | ✅ 有 |
 * | `sknrjj` 上课内容 | ❌ 没有 | ✅ 有 |
 * | `jcdm` 节次串 | ❌ 没有 | ✅ 有 |
 * | `pe` 结束节 | ✅ 有 | ❌ 没有（只有 `ps`） |
 *
 * 所以：`zc` 用柔性字符串（数字/字符串都能吃），`qsrq`/`jcdm`/`sknrjj`/`pe` 全部可空，
 * 节次在上层用 [com.smu.pocket.core.TimeUtils.sectionsOf] 统一算出。
 */
@Serializable
data class CourseData(
    /** 课程名称 */
    val kcmc: String? = null,
    /** 课程代码 */
    val kcdm: String? = null,
    /** 课序号 */
    val kcbh: String? = null,
    /** 教师姓名（可能多个，逗号分隔） */
    val teaxms: String? = null,
    /** 教学场地名称（实测 118 行里有 25 行为空串） */
    val jxcdmc: String? = null,
    /** 教学班名称，如「2024临床医学(深圳)」 */
    val jxbmc: String? = null,
    /** 讲合班形式，如「理论」「自主学习」「考试（理论）」 */
    val jxhjmc: String? = null,
    /** 考核方式名称。⚠️ 实测本接口**不返回**该字段（118 行 0 次），保留以兼容将来 */
    val khfsmc: String? = null,

    /** 星期几，1=周一 … 7=周日 */
    @Serializable(with = FlexibleIntSerializer::class)
    val xq: Int = 0,

    /**
     * 周次。可能是 `"8"`、`"12,13"`、`"13,10,15,9,4"`，也可能**整个字段缺失**。
     *
     * 实测结论（2026-10）：按日期查询（`d1`/`d2`）的响应里**可能没有 `zc`**，
     * 因为周次是由查询区间决定的，服务端不再回传。
     * 因此这里声明成非空 `String = ""` + 柔性序列化器：
     * - 字段缺失 → 用默认值 `""`（kotlinx 的行为，不报错）
     * - 字段是 `null` → 序列化器返回 `""`
     * - 字段是数字 `8` → 序列化器转成 `"8"`
     *
     * 比声明成 `String?` 更好：下游不用到处判空，"第几周"由调用方通过
     * `toCourse(fallbackWeek = ...)` 补进去（他本来就知道自己查的是第几周）。
     */
    @Serializable(with = FlexibleStringSerializer::class)
    val zc: String = "",

    /** 起始节，如 "08" */
    val ps: String? = null,
    /** 结束节，如 "04"（仅整学期总表返回） */
    val pe: String? = null,
    /** 节次串，如 "0809"、"060708"（仅按周查询返回） */
    val jcdm: String? = null,

    /** 上课日期 "2026-10-23"（仅按周查询返回）→ 反推第一周周一就靠它 */
    val qsrq: String? = null,
    /** 上课内容，如「形势与政策专题二」（仅按周查询返回） */
    val sknrjj: String? = null,
    /** 类型标记，实测按周返回 "kb" */
    val lx: String? = null,

    /** 上课开始时刻 "09:50:00" */
    val qssj: String? = null,
    /** 上课结束时刻 "11:15:00" */
    val jssj: String? = null,

    /** 学期代码 "202601" */
    val xnxqdm: String? = null,
    /** 课表配色，教务自己给的，如 "#1f86c7" */
    val bgcolor: String? = null,

    @Serializable(with = FlexibleIntSerializer::class)
    val xs: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val pkrs: Int = 0,
)

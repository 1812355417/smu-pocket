package com.smu.pocket.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 教务接口。
 *
 * 返回类型统一用 `Response<ResponseBody>`：先拿到原始字符串，
 * 再用 [com.smu.pocket.data.model.JwJson] 手动解码。
 *
 * 为什么不直接 `Response<TimetableResponse>`：
 * 1. 响应格式还没稳定（整学期 / 按周两套字段），出问题时要能一眼看到原始 JSON；
 * 2. 不额外引 converter 依赖，解码逻辑集中在 JwJson 一个文件里，好调试。
 * 等接口彻底稳定了再换 typed 版也不迟。
 */
interface JwApi {

    /**
     * 课表。实测：`POST /new/student/xsgrkb/getCalendarWeekDatas`
     *
     * - `xnxqdm`：学期代码，实测 `202601`
     * - `zc`：**实测恒为 `201`，跟网页上选第几周完全无关**，是固定值（含义未知，可能是查询模式）。
     *   真正定位周次的是 `d1`/`d2`，所以这里保留成参数，但调用方永远传 `zc`。
     * - `d1` / `d2`：日期区间，格式 `yyyy-MM-dd 00:00:00`。**d2 传下周一**（左闭右开），
     *   实测查第 9 周是 `d1=2026-10-26`、`d2=2026-11-02`。
     */
    @FormUrlEncoded
    @POST("new/student/xsgrkb/getCalendarWeekDatas")
    suspend fun timetable(
        @Field("xnxqdm") term: String = "",
        @Field("zc") zc: String = "",
    ): Response<ResponseBody>

    /**
     * 成绩。**路径已确认**：`POST /new/student/xskccj/kccjDatas`
     *
     * **分页**：实测 `total=50` 但只返回 20 行，默认页大小 20。
     * 调用方必须循环翻页，否则只会拿到 2/5 的成绩，而且**不报错**。
     */
    @FormUrlEncoded
    @POST("new/student/xskccj/kccjDatas")
    suspend fun scores(
        @Field("xnxqdm") term: String = "",
        @Field("source") source: String = "kccjlist",
        @Field("ismax") ismax: String = "1",
        @Field("page") page: Int = 1,
        @Field("rows") rows: Int = 200,
    ): Response<ResponseBody>

    companion object {
        fun create(retrofit: Retrofit): JwApi = retrofit.create(JwApi::class.java)
    }
}

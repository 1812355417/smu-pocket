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
 * 1. 响应格式有两套（整学期 / 按周），出问题时要能一眼看到原始 JSON；
 * 2. 不额外引 converter 依赖，解码逻辑集中在 JwJson 一个文件里，好调试。
 */
interface JwApi {

    /**
     * 课表。`POST /new/student/xsgrkb/getCalendarWeekDatas`
     *
     * - `xnxqdm`：学期代码，如 `202601`
     * - `zc`：**第几周**。`zc=9` → 第 9 周；`zc=` 空 → 整学期总表
     * - 按周查询的响应里带 `qsrq`（真实日期），整学期总表没有
     */
    @FormUrlEncoded
    @POST("new/student/xsgrkb/getCalendarWeekDatas")
    suspend fun timetable(
        @Field("xnxqdm") term: String = "",
        @Field("zc") zc: String = "",
    ): Response<ResponseBody>

    /**
     * 成绩。`POST /new/student/xskccj/kccjDatas`
     *
     * **分页**：实测 `total=50` 但默认只返回 20 行，调用方必须循环翻页，
     * 否则只会拿到部分成绩而且**不报错**。
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

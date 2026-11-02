package com.smu.pocket.core

import android.content.Context

/**
 * 当前学期代码的本地存储。
 *
 * 单独一个 SharedPreferences 文件（`smu_term`），不跟 cookie 混在一起：
 * cookie 是账号凭证，学期代码不是，两者的生命周期也不同
 * （学期一个学期换一次，cookie 可能几小时就过期）。
 */
class TermStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY_CODE, null)?.takeIf { TermCode.isValid(it) }

    fun save(code: String, source: String) {
        prefs.edit()
            .putString(KEY_CODE, code)
            .putString(KEY_SOURCE, source)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    /** 最近一次学期代码是从哪来的：web / cache / date / fallback */
    fun source(): String? = prefs.getString(KEY_SOURCE, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREF = "smu_term"
        const val KEY_CODE = "term_code"
        const val KEY_SOURCE = "term_source"
        const val KEY_SAVED_AT = "term_saved_at"
    }
}

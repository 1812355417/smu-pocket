package com.smu.pocket.data

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class Page<T>(val rows: List<T>, val total: Int)

/** Complete results only: reject servers that ignore page, truncate early, or exceed the safety limit. */
internal suspend fun <T> collectPages(
    maxPages: Int = 20,
    fetch: suspend (Int) -> Page<T>,
): List<T> {
    val collected = mutableListOf<T>()
    val seenPages = hashSetOf<List<T>>()
    for (number in 1..maxPages) {
        currentCoroutineContext().ensureActive()
        val page = fetch(number)
        if (page.total < collected.size + page.rows.size) throw IOException("教务分页总数不一致，请重试")
        if (page.rows.isEmpty()) {
            if (collected.size < page.total) throw IOException("教务分页提前结束，请重试")
            return collected
        }
        if (!seenPages.add(page.rows.toList())) throw IOException("教务返回重复分页，请重试")
        collected += page.rows
        if (collected.size >= page.total) return collected
    }
    throw IOException("教务分页超过安全上限，本次结果未保存")
}

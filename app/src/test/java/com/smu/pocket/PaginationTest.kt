package com.smu.pocket

import com.smu.pocket.data.Page
import com.smu.pocket.data.collectPages
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaginationTest {
    @Test fun stopsAtAccumulatedTotal() = runBlocking {
        var calls = 0
        val result = collectPages { page ->
            calls++
            Page(listOf(page), 3)
        }
        assertEquals(listOf(1, 2, 3), result)
        assertEquals(3, calls)
    }

    @Test fun rejectsDuplicatePage() {
        assertThrows(IOException::class.java) { runBlocking { collectPages { Page(listOf(1), 3) } } }
    }

    @Test fun rejectsNonAdjacentRepeatedPage() {
        assertThrows(IOException::class.java) {
            runBlocking { collectPages { page -> Page(listOf(if (page % 2 == 1) "A" else "B"), 3) } }
        }
    }

    @Test fun rejectsPrematureEmptyPage() {
        assertThrows(IOException::class.java) {
            runBlocking { collectPages { page -> Page(if (page == 1) listOf(1) else emptyList(), 3) } }
        }
    }

    @Test fun rejectsSafetyLimitWithoutReturningPartialData() {
        assertThrows(IOException::class.java) { runBlocking { collectPages(maxPages = 2) { Page(listOf(it), 3) } } }
    }

    @Test fun propagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking { collectPages<Int> { throw CancellationException("cancel") } }
        }
    }
}

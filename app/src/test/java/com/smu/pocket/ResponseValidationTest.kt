package com.smu.pocket

import com.smu.pocket.core.Semester
import com.smu.pocket.data.model.JwJson
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResponseValidationTest {
    @Test fun malformedResponsesCannotOverwriteCacheWithEmptySuccess() {
        for (body in listOf("<html>login</html>", "{}", "null", """{"code":"oops","data":[]}""")) {
            assertThrows(SerializationException::class.java) { JwJson.timetable(body) }
            assertThrows(SerializationException::class.java) { JwJson.scores(body) }
            assertThrows(SerializationException::class.java) { JwJson.examTasks(body) }
        }
    }

    @Test fun explicitEmptyListsRemainValid() {
        assertEquals(0, JwJson.timetable("""{"code":0,"data":[]}""").data.size)
        assertEquals(0, JwJson.scores("""{"rows":[],"total":0}""").rows.size)
    }

    @Test(timeout = 1000) fun hugeWeekRangesAreBoundedBeforeIteration() {
        assertEquals((1..30).toSet(), Semester.parseWeeks("1-2147483647"))
    }
}

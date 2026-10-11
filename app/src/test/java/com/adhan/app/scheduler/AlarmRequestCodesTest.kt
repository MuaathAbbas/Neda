package com.adhan.app.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRequestCodesTest {
    @Test
    fun codesAreStableAndDistinct() {
        val fire = AlarmRequestCodes.fire("phase1-test-alarm")
        assertEquals(fire, AlarmRequestCodes.fire("phase1-test-alarm"))
        assertNotEquals(fire, AlarmRequestCodes.show("phase1-test-alarm"))
        assertNotEquals(fire, AlarmRequestCodes.fire("other-alarm"))
        assertNotEquals(fire, AlarmRequestCodes.enter("phase1-test-alarm"))
        assertTrue(fire >= 0)
        assertTrue(AlarmRequestCodes.notification("phase1-test-alarm") > 0)
    }
}

package com.adhan.app.scheduler

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCompletedReceiverTest {
    @Test
    fun reschedulesAfterBootQuickBootAndUpdate() {
        assertTrue(BootCompletedReceiver.shouldReschedule(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootCompletedReceiver.shouldReschedule(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(BootCompletedReceiver.shouldReschedule("android.intent.action.QUICKBOOT_POWERON"))
        assertTrue(BootCompletedReceiver.shouldReschedule("com.htc.intent.action.QUICKBOOT_POWERON"))
        assertFalse(BootCompletedReceiver.shouldReschedule(null))
        assertFalse(BootCompletedReceiver.shouldReschedule(Intent.ACTION_BOOT_COMPLETED + ".other"))
    }
}

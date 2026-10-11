package com.adhan.app.calendar

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adhan.app.R
import com.adhan.app.alert.AlarmChannels
import java.time.LocalDate

/** One quiet notification for tomorrow's occasion. It does not ring an alarm. */
object ReminderNotifier {
    fun showTomorrow(context: Context, today: LocalDate = LocalDate.now()) {
        val tomorrow = today.plusDays(1)
        val note = DayNotes.on(tomorrow) ?: return
        val stamp = "${note.id}@$tomorrow"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST, null) == stamp) return
        AlarmChannels.ensureCreated(context)
        val notification = NotificationCompat.Builder(context, AlarmChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(title(note.kind)))
            .setContentText(context.getString(body(note.kind)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(body(note.kind))))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(note.id.hashCode(), notification)
            prefs.edit().putString(KEY_LAST, stamp).apply()
        } catch (_: SecurityException) {
            // Notifications are off. The next evening can try again.
        }
    }

    private fun title(kind: DayNoteKind): Int = when (kind) {
        DayNoteKind.ASHURA -> R.string.note_ashura_title
        DayNoteKind.WHITE_DAYS -> R.string.note_white_title
        DayNoteKind.ARAFAH -> R.string.note_arafah_title
        DayNoteKind.EID_FITR -> R.string.note_fitr_title
        DayNoteKind.EID_ADHA -> R.string.note_adha_title
        DayNoteKind.NEW_YEAR -> R.string.note_new_year_title
        DayNoteKind.LUNAR_ECLIPSE -> R.string.note_lunar_title
        DayNoteKind.SOLAR_ECLIPSE -> R.string.note_solar_title
    }

    private fun body(kind: DayNoteKind): Int = when (kind) {
        DayNoteKind.ASHURA -> R.string.note_fast_body
        DayNoteKind.WHITE_DAYS -> R.string.note_fast_body
        DayNoteKind.ARAFAH -> R.string.note_fast_body
        DayNoteKind.EID_FITR -> R.string.note_eid_body
        DayNoteKind.EID_ADHA -> R.string.note_eid_body
        DayNoteKind.NEW_YEAR -> R.string.note_new_year_body
        DayNoteKind.LUNAR_ECLIPSE -> R.string.note_eclipse_body
        DayNoteKind.SOLAR_ECLIPSE -> R.string.note_eclipse_body
    }

    private const val PREFS = "adhan_notes"
    private const val KEY_LAST = "last"
}

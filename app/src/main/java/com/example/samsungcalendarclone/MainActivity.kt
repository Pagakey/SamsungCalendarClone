package com.example.samsungcalendarclone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.samsungcalendarclone.data.reminders.ReminderAlerts
import com.example.samsungcalendarclone.ui.month.CalendarScreen
import com.example.samsungcalendarclone.ui.widget.ACTION_OPEN_DATE
import com.example.samsungcalendarclone.ui.widget.EXTRA_DATE
import com.example.samsungcalendarclone.ui.month.MonthViewModel
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: MonthViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[MonthViewModel::class.java]
        setContent { SamsungCalendarCloneTheme { CalendarScreen(model) } }
        // A restored activity already handled the intent that first launched it.
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** A tapped reminder notification opens its occurrence. */
    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_DATE) {
            intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { model.select(java.time.LocalDate.ofEpochDay(it)) }
            return
        }
        if (intent?.action != ReminderAlerts.ACTION_OPEN_EVENT) return
        val id = intent.getLongExtra(ReminderAlerts.EXTRA_EVENT_ID, -1)
        val begin = intent.getLongExtra(ReminderAlerts.EXTRA_BEGIN, -1)
        if (id > 0 && begin >= 0) model.openOccurrence(id, begin)
    }
    private val clockChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            model.onClockChanged()
            model.startClock()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockChanges, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        model.onClockChanged()
        model.startClock()
    }

    override fun onStop() {
        unregisterReceiver(clockChanges)
        model.stopClock()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        model.refreshPermissions()
    }
}

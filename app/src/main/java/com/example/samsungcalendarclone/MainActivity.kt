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
import com.example.samsungcalendarclone.ui.month.CalendarScreen
import com.example.samsungcalendarclone.ui.month.MonthViewModel
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: MonthViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[MonthViewModel::class.java]
        setContent { SamsungCalendarCloneTheme { CalendarScreen(model) } }
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

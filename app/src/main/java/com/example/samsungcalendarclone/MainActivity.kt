package com.example.samsungcalendarclone

import android.os.Bundle
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
    override fun onResume() {
        super.onResume()
        model.refreshPermissions()
    }
}

package com.example.simplesleeprecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.simplesleeprecorder.ui.navigation.AppNavigation
import com.example.simplesleeprecorder.ui.theme.SimpleSleepRecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext as SimpleSleepRecorderApp
        setContent {
            SimpleSleepRecorderTheme {
                AppNavigation(app = app)
            }
        }
    }
}

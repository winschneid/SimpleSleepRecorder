package com.example.simplesleeprecorder

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.simplesleeprecorder.data.local.SleepDatabase
import com.example.simplesleeprecorder.data.repository.SleepRepository
import com.example.simplesleeprecorder.service.SleepSessionManager
import com.example.simplesleeprecorder.ui.history.HistoryViewModel
import com.example.simplesleeprecorder.ui.home.HomeViewModel
import com.example.simplesleeprecorder.ui.result.ResultViewModel

class SimpleSleepRecorderApp : Application() {

    val sessionManager by lazy { SleepSessionManager() }
    private val database by lazy { SleepDatabase.getDatabase(this) }
    val repository by lazy { SleepRepository(database.sleepDao()) }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(this@SimpleSleepRecorderApp, sessionManager, repository)
            ResultViewModel::class.java -> ResultViewModel(repository)
            HistoryViewModel::class.java -> HistoryViewModel(repository)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        } as T
    }
}

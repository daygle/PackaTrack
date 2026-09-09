package com.packatrack.feature.settings

import androidx.lifecycle.ViewModel
import com.packatrack.data.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: PrefsStore
) : ViewModel()

package com.packatrack.feature.home

import androidx.lifecycle.ViewModel
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val repository: TrackingRepository,
    val prefs: PrefsStore
) : ViewModel()

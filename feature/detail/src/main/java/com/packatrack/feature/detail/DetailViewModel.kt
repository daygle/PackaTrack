package com.packatrack.feature.detail

import androidx.lifecycle.ViewModel
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    val repository: TrackingRepository,
    val prefs: PrefsStore
) : ViewModel()

package com.packatrack.data.di

import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataEntryPoint {
    fun repository(): TrackingRepository
    fun prefs(): PrefsStore
}

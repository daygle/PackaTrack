package com.packatrack.feature.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packatrack.core.model.Carrier
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import com.packatrack.data.TrackingRepository.RefreshOutcome
import com.packatrack.notify.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TrackingRepository,
    val prefs: PrefsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // Created once: a fresh Flow per recomposition would restart every database query.
    val activeShipments = repository.observeActive()
    val archivedShipments = repository.observeArchived()
    val recentChanges = repository.observeRecentChanges()
    val firstEventTimes = repository.observeFirstEventTimes()
    val latestEvents = repository.observeLatestEvents()

    /**
     * Newest timestamped scan per courier leg: drives the overall-status recency vote so a
     * stale DELIVERED leg cannot outrank a leg that is still moving.
     */
    val newestEventMsByLeg = repository.observeLatestEventMsByLeg()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun refreshAll(force: Boolean) = runSync { repository.refreshAll(force) }

    fun refreshShipment(shipmentId: Long) = runSync { repository.refreshShipment(shipmentId, force = true) }

    /**
     * Adds a parcel and polls just that one. Never gated by an in-flight refresh: the add
     * waits for it on the repository's lock instead of being dropped.
     */
    fun addShipment(number: String, title: String?, orderUrl: String?, carrier: Carrier?) {
        launchSync {
            val newId = repository.addShipment(number, title, orderUrl, carrier)
            repository.refreshShipment(newId, force = true)
        }
    }

    fun delete(shipmentId: Long) {
        viewModelScope.launch { repository.delete(shipmentId) }
    }

    fun setArchived(shipmentId: Long, archived: Boolean) {
        viewModelScope.launch {
            if (archived) repository.archive(shipmentId) else repository.unarchive(shipmentId)
        }
    }

    /** Manual refresh: a no-op while one is already running. */
    private fun runSync(block: suspend () -> RefreshOutcome) {
        if (_syncing.value) return
        launchSync(block)
    }

    private fun launchSync(block: suspend () -> RefreshOutcome) {
        _syncing.value = true
        viewModelScope.launch {
            try {
                Notifier.postChanges(context, block().notable)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Refresh failed", e)
            } finally {
                // Always clear the flag or a single failure would disable every refresh control.
                _syncing.value = false
            }
        }
    }
}

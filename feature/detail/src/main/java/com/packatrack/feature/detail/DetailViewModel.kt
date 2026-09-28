package com.packatrack.feature.detail

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packatrack.core.db.EventEntity
import com.packatrack.core.db.ShipmentWithLegs
import com.packatrack.core.model.Carrier
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import com.packatrack.data.TrackingRepository.RefreshOutcome
import com.packatrack.notify.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: TrackingRepository,
    val prefs: PrefsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val activeShipments: Flow<List<ShipmentWithLegs>> = repository.observeActive()

    /**
     * Newest timestamped scan per courier leg: drives the overall-status recency vote so a
     * stale DELIVERED leg cannot outrank a leg that is still moving.
     */
    val newestEventMsByLeg: Flow<Map<Long, Long>> = repository.observeLatestEventMsByLeg()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun observeShipment(id: Long): Flow<ShipmentWithLegs?> = repository.observeShipment(id)

    fun observeEvents(id: Long): Flow<List<EventEntity>> = repository.observeEvents(id)

    fun refresh(id: Long) {
        if (_syncing.value) return
        launchSync { repository.refreshShipment(id, force = true) }
    }

    fun addCourier(id: Long, number: String, carrier: Carrier?) = launchSync {
        repository.addCourier(id, number, carrier)
        repository.refreshShipment(id, force = true)
    }

    fun removeCourier(legId: Long) = perform { repository.removeCourier(legId) }

    fun addOrder(id: Long, name: String, orderUrl: String?) = perform { repository.addOrder(id, name, orderUrl) }

    fun removeOrder(orderId: Long) = perform { repository.removeOrder(orderId) }

    fun rename(id: Long, title: String?) = perform { repository.updateShipment(id, title) }

    fun combineInto(targetId: Long, sourceId: Long) = perform { repository.combineInto(targetId, sourceId) }

    /**
     * The screen closes right after this, which clears the ViewModel, so the delete must not be
     * cancelled with it (it may still be waiting for an in-flight refresh to finish).
     */
    fun delete(id: Long) = perform { withContext(NonCancellable) { repository.delete(id) } }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Parcel update failed", e)
            }
        }
    }

    private fun launchSync(block: suspend () -> RefreshOutcome) {
        _syncing.value = true
        perform {
            try {
                Notifier.postChanges(context, block().notable)
            } finally {
                _syncing.value = false
            }
        }
    }

    private companion object {
        const val TAG = "DetailViewModel"
    }
}

package com.packatrack.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap

interface ConnectivityObserver {
    fun observe(): Flow<Status>

    enum class Status {
        Available, Losing, Lost
    }
}

class NetworkConnectivityObserver(
    context: Context,
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun observe(): Flow<ConnectivityObserver.Status> {
        return callbackFlow {
            // Several networks can be active at once (Wi-Fi and cellular). Track the set so
            // losing one does not report the device as offline while another still works.
            val availableNetworks = ConcurrentHashMap.newKeySet<Network>()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    availableNetworks += network
                    trySend(ConnectivityObserver.Status.Available)
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    if (availableNetworks.size <= 1) trySend(ConnectivityObserver.Status.Losing)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    availableNetworks -= network
                    trySend(
                        if (availableNetworks.isEmpty()) ConnectivityObserver.Status.Lost
                        else ConnectivityObserver.Status.Available,
                    )
                }
            }

            runCatching {
                connectivityManager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )
            }.onFailure { close(it) }
            awaitClose {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }.distinctUntilChanged()
    }
}

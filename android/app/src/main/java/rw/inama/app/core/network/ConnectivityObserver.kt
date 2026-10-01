package rw.inama.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Exposes whether the phone currently has an internet-capable network (drives offline banners). */
class ConnectivityObserver(context: Context, scope: CoroutineScope) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun isOnlineNow(): Boolean {
        val m = manager ?: return false
        val caps = m.getNetworkCapabilities(m.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnlineNow())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        manager?.registerDefaultNetworkCallback(callback)
        trySend(isOnlineNow())
        awaitClose { manager?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, isOnlineNow())
}

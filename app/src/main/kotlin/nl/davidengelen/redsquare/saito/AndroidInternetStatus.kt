package nl.davidengelen.redsquare.saito

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

data class InternetStatus(val available: Boolean, val validated: Boolean, val networkHandle: Long)

class AndroidInternetStatus(context: Context) : Closeable {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val observed = MutableStateFlow(snapshot())
    val state = observed.asStateFlow()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { observed.value = snapshot() }
        override fun onLost(network: Network) { observed.value = snapshot() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { observed.value = snapshot() }
    }

    init { manager.registerDefaultNetworkCallback(callback) }

    private fun snapshot(): InternetStatus {
        val network = manager.activeNetwork
        val capabilities = manager.getNetworkCapabilities(network)
        return InternetStatus(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            network?.networkHandle ?: 0,
        )
    }

    override fun close() { manager.unregisterNetworkCallback(callback) }
}

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
        private var current: Network? = null
        override fun onAvailable(network: Network) { current = network }
        override fun onLost(network: Network) {
            if (current == network) {
                current = null
                observed.value = InternetStatus(false, false, 0)
            }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (current == network) observed.value = InternetStatus(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                network.networkHandle,
            )
        }
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

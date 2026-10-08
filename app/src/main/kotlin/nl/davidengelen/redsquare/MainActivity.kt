package nl.davidengelen.redsquare

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.system.Os
import java.io.File
import java.io.IOException
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.feed.FeedScreen
import nl.davidengelen.redsquare.feed.FeedEndpoints

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var wallet by remember { mutableStateOf<SaitoWallet?>(null) }
            val address = wallet?.publicKey
            var failed by remember { mutableStateOf(false) }
            var retry by remember { mutableIntStateOf(0) }
            var walletOpen by remember { mutableStateOf(false) }
            var updateAvailable by remember { mutableStateOf(false) }
            var updateCheck by remember { mutableIntStateOf(0) }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) updateCheck++
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(updateCheck) {
                val fixtureEndpoint = if (BuildConfig.DEBUG) intent.getStringExtra("reviewManifestUrl") else null
                if (updateCheck > 0 && (BuildConfig.REVIEW_BUILD || fixtureEndpoint != null)) {
                    val endpoint = fixtureEndpoint ?: PreviewUpdates.MANIFEST
                    updateAvailable = withContext(Dispatchers.IO) {
                        val version = packageManager.getPackageInfo(packageName, 0).longVersionCode
                        PreviewUpdates.hasNewerBuild(endpoint, version)
                    }
                }
            }
            LaunchedEffect(retry) {
                failed = false
                wallet = withContext(Dispatchers.IO) {
                    try {
                        val store = if (BuildConfig.DEBUG && intent.getBooleanExtra("walletCommitSyncUnavailable", false)) {
                            AndroidWalletStore(applicationContext) { descriptor ->
                                if (!File(noBackupFilesDir, "wallet.initializing").exists()) {
                                    throw IOException("Controlled filesystem sync failure")
                                }
                                Os.fsync(descriptor)
                            }
                        } else AndroidWalletStore(applicationContext)
                        store.loadOrCreate()
                    }
                    catch (_: Exception) { null }
                }
                failed = wallet == null
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        if (updateAvailable) {
                            Text(stringResource(R.string.preview_update_available))
                            TextButton(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PreviewUpdates.REVIEW_PAGE)))
                            }) { Text(stringResource(R.string.view_update)) }
                        }
                        TextButton(onClick = { walletOpen = true }) { Text(stringResource(R.string.wallet)) }
                        if (failed) {
                            Text(stringResource(R.string.wallet_recovery_error))
                            Button(onClick = { failed = false; retry++ }) { Text(stringResource(R.string.retry)) }
                        }
                        if (!(BuildConfig.DEBUG && intent.getBooleanExtra("feedDisabled", false))) {
                            FeedScreen(wallet, failed, FeedEndpoints.fromIntent(intent), Modifier.weight(1f)) { reportFullyDrawn() }
                        }
                    }
                    if (walletOpen) AlertDialog(
                        onDismissRequest = { walletOpen = false },
                        title = { Text(stringResource(R.string.your_wallet)) },
                        text = {
                            val addressLabel = stringResource(R.string.wallet_address)
                            when {
                                address != null -> Text(address!!, Modifier.semantics { contentDescription = addressLabel })
                                failed -> Text(stringResource(R.string.wallet_unavailable))
                                else -> Text(stringResource(R.string.wallet_opening))
                            }
                        },
                        confirmButton = { TextButton(onClick = { walletOpen = false }) { Text(stringResource(R.string.done)) } }
                    )
                }
            }
        }
    }
}

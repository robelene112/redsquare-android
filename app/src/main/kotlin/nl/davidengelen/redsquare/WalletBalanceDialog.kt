package nl.davidengelen.redsquare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoBalanceSnapshot

@Composable
fun WalletBalanceDialog(publicKey: String, backend: SaitoBackend?, balances: WalletBalances, onClose: () -> Unit) {
    var observation by remember(publicKey, backend) { mutableStateOf<SaitoBalanceSnapshot?>(null) }
    var loading by remember(publicKey, backend) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var copyStatus by remember(publicKey) { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(publicKey, backend, refresh) {
        loading = true
        observation = null
        observation = backend?.let { withContext(Dispatchers.IO) { balances.read(it, publicKey) } }
        loading = false
    }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(stringResource(R.string.wallet_balance_receive)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wallet_balance_reported), style = MaterialTheme.typography.labelLarge)
                        Text(when {
                            loading -> stringResource(R.string.wallet_balance_loading)
                            observation != null -> stringResource(R.string.wallet_balance_amount, observation!!.saito)
                            else -> stringResource(R.string.wallet_balance_unavailable)
                        }, style = MaterialTheme.typography.headlineSmall)
                        backend?.let { Text(stringResource(R.string.wallet_balance_source, it.domain)) }
                    }
                }
                Text(stringResource(R.string.wallet_balance_observation), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !loading, onClick = { refresh++ }) { Text(stringResource(R.string.feed_refresh)) }
                val label = stringResource(R.string.wallet_receiving_address)
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(publicKey, Modifier.semantics { contentDescription = label })
                        TextButton(onClick = {
                            copyStatus = try {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText(label, publicKey))
                                1
                            } catch (_: Exception) { 2 }
                        }) {
                            Text(stringResource(when (copyStatus) {
                                1 -> R.string.wallet_address_copied
                                2 -> R.string.wallet_address_copy_failed
                                else -> R.string.wallet_copy_address
                            }))
                        }
                    }
                }
                ReceivingAddressQr(publicKey)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } })
}

@Composable
private fun ReceivingAddressQr(publicKey: String) {
    val width = with(LocalDensity.current) { 192.dp.roundToPx() }
    val bitmap = remember(publicKey, width) {
        val matrix = QRCodeWriter().encode(publicKey, BarcodeFormat.QR_CODE, width, width,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 4))
        val pixels = IntArray(width * width) { index ->
            if (matrix[index % width, index / width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, width, width, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, stringResource(R.string.wallet_receiving_qr), Modifier.size(192.dp),
        filterQuality = FilterQuality.None)
}

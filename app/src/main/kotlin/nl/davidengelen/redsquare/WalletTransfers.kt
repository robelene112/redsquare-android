package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

/** One reviewed, foreground, device-authenticated transfer. Grants never survive recreation. */
class WalletTransfers(
    private val activity: ComponentActivity,
    private val wallet: SaitoWallet,
    private val backend: SaitoBackend,
    private val currentWallet: () -> SaitoWallet?,
    private val currentBackend: () -> SaitoBackend?,
    private val balances: WalletBalances,
    private val endpoint: String,
) {
    val backendLabel: String get() = backend.domain
    private val store = TransferStore(activity.applicationContext, wallet.publicKey)
    data class Review(val payment: SaitoPayment, val isRetry: Boolean)
    var review by mutableStateOf<Review?>(null)
        private set
    var pending by mutableStateOf<List<TransferStore.Pending>>(emptyList())
        private set
    var inclusions by mutableStateOf<Map<String, SaitoPaymentChain.Inclusion>>(emptyMap())
        private set
    var available by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    @Volatile private var attempt: Attempt? = null
    private var job: Job? = null
    private class Attempt(val reviewed: Review? = null) {
        val cancellation = CancellationSignal()
        @Volatile var authenticating = false
    }

    fun load() {
        if (busy) return
        val operation = Attempt()
        attempt = operation
        busy = true
        inclusions = emptyMap()
        job = activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val records = store.pending()
                    val funding = balances.funding(backend, wallet.publicKey)
                    val observed = funding?.let { balances.inclusions(backend, it.snapshot,
                        records.filter { record -> record.backend == backend }.map { record -> record.payment }) }
                        ?: emptyMap()
                    val history = synchronized(SaitoLocalState.lock) {
                        check(isCurrent(operation))
                        store.recordInclusions(backend, observed)
                    }
                    Triple(history, funding?.let { display(store.available(it.eligible).sumOfAmounts()) }, observed)
                }
                if (isCurrent(operation)) {
                    pending = result.first
                    available = result.second
                    inclusions = result.third
                }
            } catch (_: Exception) { if (isCurrent(operation)) message = R.string.transfer_storage_unavailable }
            finally { finish(operation) }
        }
    }

    fun prepare(recipient: String, amount: String, fee: String, memo: String) {
        if (busy) return
        val operation = Attempt()
        attempt = operation
        busy = true
        review = null
        message = null
        job = activity.lifecycleScope.launch {
            try {
                val payment = withContext(Dispatchers.IO) {
                    require(SaitoWallet.isPublicKey(recipient))
                    val paymentAmount = units(amount, positive = true)
                    val feeAmount = units(fee, positive = false)
                    require(memo.length <= 4096 && memo.toByteArray(Charsets.UTF_8).size <= 4096)
                    val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                    val candidates = store.available(funding.eligible).sortedByDescending { it.amount }
                    var total = BigInteger.ZERO
                    val chosen = candidates.takeWhile { input ->
                        if (total >= paymentAmount + feeAmount) false else { total += input.amount; true }
                    }
                    require(total >= paymentAmount + feeAmount && chosen.size in 1..254)
                    SaitoPeer(wallet, endpoint).use { it.authenticate() }
                    check(isCurrent(operation))
                    SaitoPayment.create(wallet, chosen, recipient, paymentAmount, feeAmount,
                        System.currentTimeMillis(), UUID.randomUUID().toString(), memo)
                }
                if (isCurrent(operation)) review = Review(payment, isRetry = false)
            } catch (_: Exception) { if (isCurrent(operation)) message = R.string.transfer_review_failed }
            finally { finish(operation) }
        }
    }

    fun prepareRetry(id: String) {
        if (busy) return
        val operation = Attempt()
        attempt = operation
        busy = true
        review = null
        message = null
        job = activity.lifecycleScope.launch {
            try {
                val payment = withContext(Dispatchers.IO) {
                    val saved = retained(id)
                    val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                    require(saved.inputs.all { it in funding.eligible })
                    SaitoPeer(wallet, endpoint).use { it.authenticate() }
                    check(isCurrent(operation))
                    saved
                }
                if (isCurrent(operation)) review = Review(payment, isRetry = true)
            } catch (_: Exception) { if (isCurrent(operation)) message = R.string.transfer_review_failed }
            finally { finish(operation) }
        }
    }

    private fun retained(id: String): SaitoPayment = store.pending().single {
        it.backend == backend && it.payment.transaction.id == id
    }.payment

    fun authenticateAndSend() {
        if (busy) return
        val reviewed = review ?: return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            message = R.string.wallet_auth_setup
            return
        }
        val operation = Attempt(reviewed)
        attempt = operation
        busy = true
        message = null
        operation.authenticating = true
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.transfer_auth_title))
                .setSubtitle(activity.getString(R.string.transfer_auth_subtitle))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            builder.build().authenticate(operation.cancellation, activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!isCurrent(operation) || !operation.authenticating) return
                        operation.authenticating = false
                        send(operation)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!isCurrent(operation) || !operation.authenticating) return
                        message = if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) R.string.transfer_cancelled
                            else R.string.wallet_auth_unavailable
                        finish(operation)
                    }
                })
        } catch (_: Exception) { message = R.string.wallet_auth_unavailable; finish(operation) }
    }

    private fun send(operation: Attempt) {
        val reviewed = requireNotNull(operation.reviewed)
        val payment = reviewed.payment
        job = activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    check(isCurrent(operation))
                    val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                    val candidates = if (reviewed.isRetry) funding.eligible else store.available(funding.eligible)
                    require(payment.inputs.all { it in candidates })
                    SaitoPeer(wallet, endpoint).use { peer ->
                        peer.submit(payment.transaction) {
                            check(isCurrent(operation))
                            check(AndroidWalletStore(activity.applicationContext).loadExisting().publicKey == wallet.publicKey)
                            check(AndroidBackendStore(activity.applicationContext).load() == backend)
                            if (reviewed.isRetry) {
                                check(retained(payment.transaction.id).transaction.encoded() == payment.transaction.encoded())
                            } else store.reserve(backend, payment)
                        }
                        peer.finishBroadcast()
                    }
                    store.pending() to display(store.available(funding.eligible).sumOfAmounts())
                }
                if (isCurrent(operation)) {
                    pending = result.first
                    available = result.second
                    review = null
                }
            } catch (_: Exception) {
                // Saving a candidate can succeed before a later error. Never undo its reservation.
                val retained = withContext(Dispatchers.IO) { runCatching { store.pending() }.getOrNull() }
                if (isCurrent(operation)) {
                    if (retained == null) { available = null; message = R.string.transfer_storage_unavailable }
                    else {
                        pending = retained
                        if (retained.any { it.payment.transaction.id == payment.transaction.id }) {
                            review = null; available = null
                        } else message = R.string.transfer_send_failed
                    }
                }
            } finally { finish(operation) }
        }
    }

    private fun isCurrent(operation: Attempt) = attempt === operation && !operation.cancellation.isCanceled &&
        currentWallet() === wallet && currentBackend() == backend && !activity.isFinishing && !activity.isDestroyed

    private fun finish(operation: Attempt) {
        if (attempt !== operation) return
        attempt = null
        busy = false
        operation.authenticating = false
        operation.cancellation.cancel()
    }

    fun cancel() {
        val operation = attempt
        attempt = null
        job?.cancel()
        operation?.cancellation?.cancel()
        busy = false
        review = null
    }

    companion object {
        fun display(amount: BigInteger): String = BigDecimal(amount, 8).stripTrailingZeros().toPlainString()
        private fun units(text: String, positive: Boolean): BigInteger {
            require(text.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,8})?")))
            return BigDecimal(text).movePointRight(8).toBigIntegerExact().also {
                require(it.signum() >= if (positive) 1 else 0)
                require(it.bitLength() <= 64)
            }
        }
        private fun List<SaitoBalanceSnapshot.Slip>.sumOfAmounts() = fold(BigInteger.ZERO) { total, slip -> total + slip.amount }
    }
}

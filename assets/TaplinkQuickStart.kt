package example.taplink

import android.os.Handler
import android.os.Looper
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implement with the consumer's durable order repository, not an in-memory map.
 * Methods run on the consumer's IO/callback executor and must be thread-safe.
 */
interface PaymentJournal {
    /**
     * Atomically reserve this order and persist request ID, amount, currency and the
     * app's selected terminal/endpoint before sending. Return false when the order
     * already has an active/unresolved attempt. A persistence failure must throw,
     * preventing transmission. The caller invokes sale() on its IO dispatcher.
     */
    fun reserve(request: SaleRequest): Boolean

    /**
     * Persist the result against the captured original request ID. Only confirmed
     * final outcomes resolve an attempt; PROCESSING/unknown stay pending. Apply
     * order updates idempotently so a later query cannot settle the order twice.
     */
    fun recordResult(requestId: String, result: PaymentResult)

    /** Persist the technical error; retain the attempt for reconciliation. */
    fun recordError(requestId: String, error: PaymentError)
}

/**
 * One instance owned by the app's payment session, after TaplinkSDK.init().
 * The consumer owns durable storage, authentication setup and query scheduling.
 * This adapter never retries a monetary operation automatically.
 */
class TaplinkQuickStart(
    private val journal: PaymentJournal,
    // Supply an app-owned background executor, kept alive while journal writes are pending.
    private val callbackExecutor: Executor,
    private val onUpdate: (Update) -> Unit
) : AutoCloseable {
    sealed class Update {
        object Connected : Update()
        data class Disconnected(val reason: String) : Update()
        data class ConnectionFailed(val error: ConnectionError) : Update()
        data class Progress(val requestId: String, val event: PaymentEvent) : Update()
        data class Approved(val requestId: String, val result: PaymentResult) : Update()
        data class Declined(val requestId: String, val result: PaymentResult) : Update()
        data class Unresolved(
            val requestId: String,
            val result: PaymentResult? = null,
            val error: PaymentError? = null
        ) : Update()
        data class TechnicalFailure(val requestId: String, val error: PaymentError) : Update()
        data class SubmissionBlocked(val requestId: String) : Update()
    }

    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)

    private fun emit(update: Update) {
        main.post { if (!closed.get()) onUpdate(update) }
    }

    fun connect(config: ConnectionConfig) {
        check(!closed.get()) { "Payment session is closed" }
        check(TaplinkSDK.isInitialized()) { "Configure credentials and initialize SDK first" }
        TaplinkSDK.connect(config, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                // These values may be placeholders before INIT; do not use them as proof of identity.
                emit(Update.Connected)
            }
            override fun onDisconnected(reason: String) {
                // Pending records stay durable; the consumer schedules recovery after reconnect.
                emit(Update.Disconnected(reason))
            }
            override fun onError(error: ConnectionError) {
                emit(Update.ConnectionFailed(error))
            }
        })
    }

    /**
     * Call after the app verifies the selected terminal identity via getTerminalInfo().
     * Use the consumer's IO dispatcher because reserve() writes durable storage.
     */
    fun sale(request: SaleRequest) {
        check(!closed.get()) { "Payment session is closed" }
        check(TaplinkSDK.isConnected()) { "Connect before sending payment" }
        if (!journal.reserve(request)) {
            emit(Update.SubmissionBlocked(request.transactionRequestId))
            return
        }
        // Once reserved, exceptions/disconnects leave the attempt available for query.
        TaplinkSDK.getClient().sale(request, callback(request.transactionRequestId))
    }

    /**
     * One query of an existing attempt, after the app compares getTerminalInfo() with
     * the original attempt's saved terminal identity. The consumer bounds retries.
     */
    fun query(originalRequestId: String) {
        require(originalRequestId.isNotBlank())
        check(!closed.get()) { "Payment session is closed" }
        check(TaplinkSDK.isConnected()) { "Reconnect to the original terminal before query" }
        TaplinkSDK.getClient().query(
            QueryRequest.byTransactionRequestId(originalRequestId),
            callback(originalRequestId, isQuery = true)
        )
    }

    private fun callback(originalRequestId: String, isQuery: Boolean = false) = object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            callbackExecutor.execute {
                journal.recordResult(originalRequestId, result)
                val update = when {
                    result.isSuccess() -> Update.Approved(originalRequestId, result)
                    result.isFailed() -> Update.Declined(originalRequestId, result)
                    result.isProcessing() -> Update.Unresolved(originalRequestId, result = result)
                    else -> Update.Unresolved(originalRequestId, result = result)
                }
                emit(update)
            }
        }
        override fun onFailure(error: PaymentError) {
            callbackExecutor.execute {
                journal.recordError(originalRequestId, error)
                emit(
                    if (isQuery || error.code in setOf("213", "304", "306", "308", "312")) {
                        Update.Unresolved(originalRequestId, error = error)
                    } else {
                        // Still pending in the journal. Triage the cause; do not unlock checkout here.
                        Update.TechnicalFailure(originalRequestId, error)
                    }
                )
            }
            // The captured original ID remains usable even when error.transactionRequestId is null.
        }
        override fun onProgress(event: PaymentEvent) {
            emit(Update.Progress(originalRequestId, event))
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            TaplinkSDK.disconnect()
            TaplinkSDK.removeConnectionListener()
        }
    }

    companion object {
        fun saleRequest(
            orderId: String,
            requestId: String,
            amountCents: Long,
            currency: String
        ): SaleRequest = SaleRequest.builder()
            .setReferenceOrderId(orderId)
            .setTransactionRequestId(requestId)
            .setAmount(AmountInfo.of(amountCents, currency))
            .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
            .setDescription("Card purchase")
            .build()
    }
}

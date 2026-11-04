package example.taplink

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AbortRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthAmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ForcedAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.IncrementalAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.PostAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.RefundRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TipAdjustRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.VoidRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest
import java.math.BigDecimal

/** Request construction only. Persist each new operation before calling TaplinkClient. */
object TransactionRecipes {
    fun referencedRefund(newId: String, originalTxnId: String, cents: Long): RefundRequest =
        RefundRequest.referencedBuilder()
            .setTransactionRequestId(newId)
            .setOriginalTransactionId(originalTxnId)
            .setAmount(AmountInfo.of(cents, "USD"))
            .build()

    fun nonReferencedRefund(newId: String, orderId: String, cents: Long): RefundRequest =
        RefundRequest.nonReferencedBuilder()
            .setTransactionRequestId(newId)
            .setReferenceOrderId(orderId)
            .setAmount(AmountInfo.of(cents, "USD"))
            .build()

    fun voidRequest(newId: String, originalTxnId: String): VoidRequest =
        VoidRequest.builder()
            .setTransactionRequestId(newId)
            .setOriginalTransactionId(originalTxnId)
            .build()

    fun auth(newId: String, orderId: String, cents: Long): AuthRequest =
        AuthRequest.builder()
            .setTransactionRequestId(newId)
            .setReferenceOrderId(orderId)
            .setAmount(AuthAmountInfo(BigDecimal.valueOf(cents), "USD"))
            .build()

    fun postAuth(newId: String, originalAuthId: String, cents: Long): PostAuthRequest =
        PostAuthRequest.builder()
            .setTransactionRequestId(newId)
            .setOriginalTransactionId(originalAuthId)
            .setAmount(AmountInfo.of(cents, "USD"))
            .build()

    fun incrementalAuth(newId: String, originalAuthId: String, cents: Long): IncrementalAuthRequest =
        IncrementalAuthRequest.builder()
            .setTransactionRequestId(newId)
            .setOriginalTransactionId(originalAuthId)
            .setAmount(AuthAmountInfo(BigDecimal.valueOf(cents), "USD"))
            .build()

    fun forcedAuth(newId: String, orderId: String, cents: Long): ForcedAuthRequest =
        ForcedAuthRequest.builder()
            .setTransactionRequestId(newId)
            .setReferenceOrderId(orderId)
            .setAmount(AuthAmountInfo(BigDecimal.valueOf(cents), "USD"))
            .build()

    fun tipAdjust(newId: String, originalTxnId: String, tipCents: Long): TipAdjustRequest =
        TipAdjustRequest.builder()
            .setTransactionRequestId(newId)
            .setOriginalTransactionId(originalTxnId)
            .setTipAmount(BigDecimal.valueOf(tipCents))
            .build()

    fun batchClose(newId: String): BatchCloseRequest =
        BatchCloseRequest.builder()
            .setTransactionRequestId(newId)
            .setPrintReceipt(PrintReceipt.NONE)
            .build()

    // Abort targets the existing request; this builder has no setTransactionRequestId().
    fun abort(originalRequestId: String): AbortRequest =
        AbortRequest.builder()
            .setOriginalTransactionRequestId(originalRequestId)
            .build()
}

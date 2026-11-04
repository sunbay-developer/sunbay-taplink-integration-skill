# TapLink SDK 1.1.0 protocol and operation reference

Checked 2026-11-04 against the [pinned SDK/Tapro source baseline](references/source-baseline.md). This file keeps its original path for installed skill users. Setup: [quick start](references/quick-start.md). Recovery: [recovery guide](references/recovery.md).

## Public API map

| Type | Import |
|---|---|
| SDK / client | `com.sunmi.tapro.taplink.sdk.TaplinkSDK`, `.TaplinkClient` |
| Configuration | `com.sunmi.tapro.taplink.sdk.config.TaplinkConfig`, `.ConnectionConfig` |
| Callbacks | `com.sunmi.tapro.taplink.sdk.callback.PaymentCallback`, `.ConnectionListener`, `.DiscoveryListener`, `.TerminalInfoCallback` |
| Amount / payment category / events | `com.sunmi.tapro.taplink.sdk.model.common.AmountInfo`, `.PaymentMethodInfo`, `.PaymentCategory`, `.PaymentEvent` |
| Query | `com.sunmi.tapro.taplink.sdk.model.request.QueryRequest` |
| Typed transaction requests | `com.sunmi.tapro.taplink.sdk.model.request.transaction.*` |
| Batch close | `com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest` |
| Results | `com.sunmi.tapro.taplink.sdk.model.response.PaymentResult`, `.TerminalInfo` |
| Errors | `com.sunmi.tapro.taplink.sdk.error.PaymentError`, `.ConnectionError` |
| Mode / protocol / receipt | `com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy`, `.CableProtocol`, `.PrintReceipt` |

Entries beginning with `.` in the same cell share the preceding package. The Kotlin assets have explicit full imports. Use `TaplinkSDK.getClient().sale(request, callback)` and corresponding client methods. The SDK API is callback-based; any suspend/Flow interface is the consumer's adapter. Coroutine cancellation alone does not abort the terminal payment.

## Identifiers, amounts and results

- `referenceOrderId`: the merchant's order reference; SALE/AUTH-style builders require 6–32 characters, using letters, digits, `_`, `-`, `|`, `*`.
- `transactionRequestId`: a UUID generated for each new monetary operation; persist before transmission. Keep the same original ID when querying that operation.
- `transactionId`: the gateway's returned transaction identifier. Follow-up builders accept `originalTransactionId` or `originalTransactionRequestId`; choose one known reference.
- `AmountInfo`: integer smallest currency units. `AmountInfo.of(1000L, "USD")` or `AmountInfo(BigDecimal("1000"), "USD")` means $10.00. The zero-argument `AmountInfo()` constructor is unavailable.
- `AuthAmountInfo`: order amount and currency for AUTH, INCREMENT_AUTH and FORCED_AUTH. POST_AUTH uses `AmountInfo`.
- Convert decimal UI input with an exact decimal/currency-exponent conversion and reject fractional minor units. For USD text: `BigDecimal(text).movePointRight(2).longValueExact()`. Avoid Double/Float conversions and assumptions that all currencies have two decimal places.
- `PaymentResult.isSuccess()`: `code == "100" && transactionStatus == "SUCCESS"`. `isProcessing()`: status `PROCESSING`. `isFailed()`: status `FAILED`. Preserve an unresolved fallback for missing/unrecognized statuses.
- `onProgress` carries `eventCode` and `message`; display progress on the main thread. Progress does not settle the order.

Capture the original ID in the callback closure. Persist sanitized identifiers, final status, approved amounts and appropriate receipt details. Treat result fields as nullable where declared; never dump the full result/card data to logs.

## Transaction recipes

[TransactionRecipes.kt](assets/TransactionRecipes.kt) contains compilable builders for the operations below. Every new monetary operation needs its own persisted UUID, callback and duplicate-submission protection. These recipes build requests only; the application authorizes and sends them.

| Operation | Client call / request | Required operation data |
|---|---|---|
| SALE | `sale(SaleRequest, callback)` | Order reference, new ID, AmountInfo |
| Referenced refund | `refund(RefundRequest.referencedBuilder()..., callback)` | New ID, original transaction reference, AmountInfo |
| Non-referenced refund | `refund(RefundRequest.nonReferencedBuilder()..., callback)` | New ID, order reference, AmountInfo; card interaction and channel support |
| VOID | `void(VoidRequest, callback)` | New ID and original transaction reference |
| AUTH | `auth(AuthRequest, callback)` | New ID, order reference, AuthAmountInfo |
| POST_AUTH | `postAuth(PostAuthRequest, callback)` | New ID, original authorization reference, AmountInfo |
| INCREMENT_AUTH | `incrementalAuth(IncrementalAuthRequest, callback)` | New ID, original authorization reference, positive AuthAmountInfo |
| FORCED_AUTH | `forcedAuth(ForcedAuthRequest, callback)` | New ID, order reference, AuthAmountInfo; supported terminal/channel flow |
| TIP_ADJUST | `tipAdjust(TipAdjustRequest, callback)` | New ID, original transaction reference, tip amount in minor units |
| BATCH_CLOSE | `batchClose(BatchCloseRequest, callback)` | New ID; explicitly select receipt behavior if needed |
| ABORT | `abort(AbortRequest, callback)` | Original ongoing transaction request ID |
| QUERY | `query(QueryRequest, callback)` | Original transaction ID or original request ID |

Example dispatch (after persisting `newRequestId` and its order/terminal association):

```kotlin
val client = TaplinkSDK.getClient()
val request = TransactionRecipes.referencedRefund(newRequestId, originalSaleId, 500L)
client.refund(request, paymentCallback)
```

The **referenced** refund builder has no `setPaymentMethod`; avoid copying that method from SALE/non-referenced refund. For a request-ID reference, replace `.setOriginalTransactionId(originalTxnId)` with `.setOriginalTransactionRequestId(originalRequestId)` in the recipe.

Void eligibility follows original transaction/batch state and processor rules; use refund for eligible settled transactions. Do not promise a fixed same-day window or issuer posting date. Tapro 1.0.5 rejects QR void with 303. Request availability in the SDK still requires Host/channel/merchant enablement.

Abort targets the original operation and has no `.setTransactionRequestId(newId)`. Use it while the terminal flow is cancellable; reconcile the original final result afterward. Use the applicable void/refund operation for an already-authorized transaction.

`ForcedAuthRequest.Builder` has no `setForcedAuthCode`. Follow the supported Tapro flow rather than inventing a field from a different API. Advanced transactions need channel-specific validation before rollout.

## Query

```kotlin
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest

val byRequest = QueryRequest.byTransactionRequestId(originalRequestId)
val byTransaction = QueryRequest.byTransactionId(originalTransactionId)
// Choose one known identifier, then:
TaplinkSDK.getClient().query(byRequest, paymentCallback)
```

There is no order-ID query setter in this version. The query result describes the original operation. Keep query attempts bounded and separate from monetary attempts; see [recovery](references/recovery.md).

## Tips, signature and receipts

- `orderAmount` excludes tip. Known tip goes into `amount.tipAmount`, in minor units. For terminal-entered tips, use `tipConfig` instead; setting both is invalid.
- `TipConfig(useHostConfig = true)` uses the terminal's configured policy. Explicit settings use `useHostConfig = false` with appropriate `onScreenTip`, `tipMode`, `tipWithTax` and suggestions. Validate the intended channel behavior.
- `SignatureConfig` belongs in `model.common`; use supported factories such as `SignatureConfig.onScreen()` or the explicit constructor. Host policy and terminal/channel capabilities still apply.
- The SDK exposes `tipAdjust`, but this does not establish enablement for every processor. Tapro's pinned 1.0.5 code has no complete `allowTipAdjustment` request path; avoid advertising an SDK flag to enable it. Confirm EPX/other channel capability separately.
- `printReceipt` uses `PrintReceipt`; transaction defaults commonly use `AUTO`. Batch close supports batch-specific receipt selection. Choose deliberately instead of assuming no printing.

## Read terminal metadata

```kotlin
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.TerminalInfoCallback
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo

TaplinkSDK.getClient().getTerminalInfo(object : TerminalInfoCallback {
    override fun onSuccess(info: TerminalInfo) {
        val merchantId = info.merchantId
        val terminalSn = info.terminal.sn
        // Persist/display only the fields needed by the app; UI work goes to main.
    }
    override fun onFailure(error: PaymentError) {
        // Retain actual code/message; inspect onboarding, authentication and Host support.
    }
})
```

This reads the connected terminal and merchant metadata: `terminal.sn`, vendor/model/TIDs, merchant identity/address/MIDs. It has no transaction history, terminal list, busy flag or connected-POS owner. Tapro 1.0.5 handles this read even during payment activity. SDK 1.1.0 passes through Host errors instead of promising code 352.

## Scope of this version

SDK 1.1.0 supplies local On-Device, explicit Cross-Device and Sub-Screen APIs. Cloud integration, later SAF APIs and proposed multi-terminal reservation/`terminalSn` connection APIs require their own matching version/documentation. Continue using the user's selected 1.1.0 API surface for this skill's baseline.

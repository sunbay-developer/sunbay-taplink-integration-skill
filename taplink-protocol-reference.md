# TapLink SDK Protocol Reference (Snapshot)

> **Last Updated:** 2026-08-14  
> **SDK Version:** 1.0.8 (released — LAN discovery & QR scan, signature/tip config source, Jackson migration)  
> **This is a dated cache** — verify against the official SDK source before implementing.

---

## Table of Contents

1. [SDK Initialization](#sdk-initialization)
2. [Connection Modes](#connection-modes)
3. [Transaction Types & Request/Response](#transaction-types--requestresponse)
4. [Amount Handling](#amount-handling)
5. [Error Codes](#error-codes)
6. [Callback & Thread Safety](#callback--thread-safety)
7. [Recovery & Idempotency](#recovery--idempotency)
8. [Tips](#tips)
9. [Security & PCI](#security--pci)
10. [Official References](#official-references)

---

## SDK Initialization

### TaplinkConfig

```kotlin
data class TaplinkConfig(
    val appId: String,                          // Your app identifier (from onboarding) — required
    val secretKey: String,                      // Secret key for HMAC — required
    val merchantId: String? = null,             // Merchant account ID — OPTIONAL (merchant-less config supported since v1.0.7)
    val logEnabled: Boolean = false,            // Disable in Release builds
    val logLevel: LogLevel = LogLevel.INFO,
    val timeout: Int = 180,                     // Default transaction timeout (seconds)
    val defaultStaffInfo: StaffInfo? = null,
    val defaultDeviceInfo: DeviceInfo? = null
    // version defaults to the SDK's own BuildConfig.VERSION_NAME
)
```

> **Verify field order/names against the SDK source** — `config/TaplinkConfig.kt`. `merchantId` is
> nullable in the current SDK, but most onboarding flows still require it; confirm with your account.

### Initialize (once per app lifecycle)

```kotlin
TaplinkSDK.init(context: Context, config: TaplinkConfig)
```

**Behavior:**
- Must be called before any payment or connection attempt.
- Typically called in `Application.onCreate()`.
- Credentials are **never exposed** after init; SDK handles them internally.

---

## Connection Modes

### Mode A: App-to-App (Intent)

**When:** POS app and Tapro are on the same Android device.

```kotlin
val config = ConnectionConfig.createAppMode()
// or explicit:
val config = ConnectionConfig()
    .setConnectionMode(ConnectionMode.APP_TO_APP)

TaplinkSDK.connect(config, connectionListener)
```

**Intent action:** `com.sunmi.tapro.taplink.action.SERVICE` (internal)  
**Lifecycle:** Automatic discovery and connection via Intent.

### Mode B: LAN (Network via mDNS)

**When:** POS app is on a different device from Tapro, connected via local network.

```kotlin
// Option A — explicit host/port (or reuse a cached address):
val config = ConnectionConfig.createLanMode("192.168.1.100", 8443) // or createLanMode() to reuse cache
TaplinkSDK.connect(config, connectionListener)

// Option B — obtain the address first, then connect via the SAME LAN flow.
// These APIs RETURN host/port via DiscoveryListener; they do NOT connect themselves. (since v1.0.8)
TaplinkSDK.discoverLanServices(object : DiscoveryListener {      // one-shot mDNS (_taplink._tcp), 15s timeout
    override fun onDiscovered(services: List<DiscoveredService>) {
        val t = services.firstOrNull() ?: return                 // DiscoveredService(name, host, port)
        TaplinkSDK.connect(ConnectionConfig.createLanMode(t.host, t.port), connectionListener)
    }
    override fun onError(error: ConnectionError) { /* E501/E503 ... */ }
})
// Or scan a `lan://host/port` QR shown on the terminal (built-in CameraX/ZXing scanner):
TaplinkSDK.scanLanQrCode(discoveryListener)

// One-call variants that discover/scan AND connect (report via ConnectionListener):
TaplinkSDK.autoDiscoverAndConnect(connectionListener)
TaplinkSDK.scanAndConnect(connectionListener)
```

**Discovery process:**
- mDNS discovery (`discoverLanServices` / `autoDiscoverAndConnect`) uses Android NSD — no extra dependency.
- QR scan (`scanLanQrCode` / `scanAndConnect`) reads a `lan://host/port` code and needs CameraX + ZXing
  (`implementation`, not transitive — add them to your app module) plus `CAMERA` permission (declared by SDK).
- Discovery/scan errors: `E501` none found, `E502` all failed to connect, `E503` already in progress,
  `E504` scan cancelled, `E505` camera denied, `E506` no usable camera.
- There is **no** `DiscoveryConfig` / `startDiscovery` API — verify against `TaplinkApi.kt`.

**Connection protocol:**
- WebSocket over WLAN.
- Port range: 8443–8453 (configurable).
- TLS certificate pinning (if configured).

**Discovered service model** (returned by `discoverLanServices` / `scanLanQrCode`):
```kotlin
data class DiscoveredService(
    val name: String,        // mDNS instance name (placeholder "QR" for scan results)
    val host: String,        // resolved host / IP, e.g. "192.168.1.100"
    val port: Int            // service port, e.g. 8443
)
```

### Mode C: Cable (USB/Serial)

**When:** POS app connects to Tapro via USB or serial cable.

```kotlin
val config = ConnectionConfig.createCableMode()
// or explicit:
val config = ConnectionConfig()
    .setConnectionMode(ConnectionMode.CABLE)
    .setCableProtocol(CableProtocol.AUTO)  // Recommended: auto-detect

TaplinkSDK.connect(config, connectionListener)
```

**Protocol priority (auto-detect):**
1. `USB_VSP` (CDC-ACM, Virtual Serial Port) — preferred, most compatible
2. `RS232` (RS-232 serial, 9600 baud, hex frames)
3. `USB_AOA` (Android Open Accessory, fallback for older hardware)

**Explicit protocol selection:**
```kotlin
.setCableProtocol(CableProtocol.USB_VSP)   // Force USB VSP
.setCableProtocol(CableProtocol.RS232)     // Force RS-232
.setCableProtocol(CableProtocol.USB_AOA)   // Force USB AOA
```

**Android 12+ permissions:**
- Manifest: `<uses-permission android:name="android.permission.USB_HOST" />`
- Runtime: request `android.permission.USB_HOST` at runtime.

---

## Transaction Types & Request/Response

### Sale (Charge)

**Request:**
```kotlin
val request = SaleRequest.builder()
    .setReferenceOrderId(String)           // 6–32 chars, business order ID
    .setTransactionRequestId(String)       // Unique, globally unique
    .setAmount(AmountInfo(...))            // Required: in cents
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .setDescription(String)                // Optional: receipt note
    .setPrintReceipt(PrintReceipt.AUTO)    // AUTO / MERCHANT / CUSTOMER / BOTH / NONE
    .setTipConfig(TipConfig(...))          // Optional: interactive on-screen tip prompts
    .build()
    // Note: a fixed/known tip is passed inside AmountInfo(tipAmount = ...), NOT as a builder setter.
    // There is no SaleRequest.builder().setTipAmount(...).
```

**Response:**
```kotlin
data class PaymentResult(
    val code: String,                      // Gateway/response code: "100" = reached gateway (NOT approval)
    val transactionId: String,             // Unique transaction ID from terminal
    val transactionRequestId: String?,     // Echo of request ID
    val transactionStatus: String,         // "SUCCESS" / "FAILED" / "PROCESSING"
    val message: String,                   // User-facing message
    val cardInfo: CardInfo?,               // Masked card details (last4, brand) — see CardInfo
    val authCode: String?,                 // Authorization code from bank
    val rrn: String?,                      // Retrieval Reference Number
    val createTime: String?,               // Transaction create time (string)
    val completeTime: String?,             // Transaction complete time (string)
    val receiptJson: String?               // Receipt content (JSON) if printReceipt requests it
    // ...many more optional fields; see model/response/PaymentResult.kt for the full list
) {
    // Approved requires BOTH the gateway code "100" AND transactionStatus "SUCCESS":
    fun isSuccess(): Boolean = code == "100" && transactionStatus == "SUCCESS"
    fun isProcessing(): Boolean = transactionStatus == "PROCESSING"
    fun isFailed(): Boolean = transactionStatus == "FAILED"   // a decline is a final result via onSuccess
}
```

> The field list above is **simplified** — the real `PaymentResult` has ~30 fields (card details live in
> `CardInfo`, times are strings, receipt is `receiptJson`). Always check the source before relying on a field name.

### Refund

**Referenced (with original transaction ID):**
```kotlin
val request = RefundRequest.referencedBuilder()
    .setTransactionRequestId(String)       // New unique ID
    .setOriginalTransactionId(String)      // From original sale
    .setAmount(AmountInfo(...))
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .setDescription(String)
    .build()
```

**Non-referenced (no original transaction ID, requires card swipe):**
```kotlin
val request = RefundRequest.nonReferencedBuilder()
    .setTransactionRequestId(String)
    .setReferenceOrderId(String)           // New refund order ID
    .setAmount(AmountInfo(...))
    .setDescription(String)
    .build()
```

**Response:** Same as `PaymentResult` (see Sale above).

### Void (Same-day reverse)

**Request:**
```kotlin
val request = VoidRequest.builder()
    .setTransactionRequestId(String)       // New unique ID
    .setOriginalTransactionId(String)      // From original sale (same-day only)
    .build()
```

**Response:** `PaymentResult`

**Constraints:**
- Only works for **same-day** transactions.
- Pre-settlement only (before batch close).
- For cross-day voids, use Refund instead.

### Authorization (Pre-Auth)

**Request:**
```kotlin
val request = AuthRequest.builder()
    .setReferenceOrderId(String)
    .setTransactionRequestId(String)
    .setAmount(AuthAmountInfo(
        orderAmount = BigDecimal(...),     // In cents (smallest unit)
        pricingCurrency = String           // "USD", "EUR", etc.
    ))
    .setDescription(String)
    .build()
```

**Response:** `PaymentResult` with `authCode` and `transactionId` for later post-auth.

### Post-Auth (Completion of Pre-Auth)

**Request:**
```kotlin
val request = PostAuthRequest.builder()
    .setTransactionRequestId(String)       // New unique ID
    .setOriginalTransactionId(String)      // From auth()
    .setAmount(AmountInfo(...))            // Final amount (may differ from auth)
    .build()
```

**Response:** `PaymentResult`

### Query (Status check)

**Request:**
```kotlin
val request = QueryRequest()
    .setTransactionRequestId(String)       // Original transaction request ID
```

**Response:** `PaymentResult` with **only** `transactionStatus` and `transactionId` populated.

### Batch Close (End-of-day settlement)

**Request:**
```kotlin
val request = BatchCloseRequest.builder()
    .setTransactionRequestId(String)
    .setDescription(String)
    .build()
```

**Response:** `PaymentResult` with settlement summary.

### Abort (Cancel in-progress transaction)

**Request:**
```kotlin
val request = AbortRequest.builder()
    .setTransactionRequestId(String)
    .build()
```

**Response:** `PaymentResult` with abort status.

**Constraint:** Only works before the transaction is sent to the bank. Once authorized, use Void/Refund.

---

## Amount Handling

### AmountInfo (for Sale, Refund, PostAuth)

```kotlin
data class AmountInfo(
    val orderAmount: BigDecimal,           // In **cents** (smallest currency unit); excludes tip
    val pricingCurrency: String,           // "USD", "EUR", "CNY", "JPY", etc.
    val tipAmount: BigDecimal? = null,     // Optional pre-known tip (in cents)
    val taxAmount: BigDecimal? = null,     // Optional tax (in cents)
    val surchargeAmount: BigDecimal? = null,
    val cashbackAmount: BigDecimal? = null
)
// Convenience: AmountInfo.of(1000L, "USD")  // $10.00
```

**Note:** `TipConfig` (on-screen tip prompts) is **not** part of `AmountInfo` — set it on the request builder
via `.setTipConfig(...)` (since v1.0.6). Use a fixed `tipAmount` **or** an interactive `tipConfig`, not both.

### AuthAmountInfo (for Auth)

```kotlin
data class AuthAmountInfo(
    val orderAmount: BigDecimal,           // In cents (smallest unit)
    val pricingCurrency: String            // ISO 4217, 3 chars
)
```

### Conversion examples

| Display | Unit | Code |
|---|---|---|
| $10.00 | USD (cents) | `BigDecimal("1000")` |
| €10.00 | EUR (cents) | `BigDecimal("1000")` |
| ¥10 | JPY (yen, no decimal) | `BigDecimal("10")` |
| ¥10.00 | CNY (fen, 1/100 yuan) | `BigDecimal("1000")` |

**Never:**
- Use floating-point: `1000.0` ❌
- Mix units: `10.00` for $10 ❌
- Use `BigDecimal` constructor with double: `BigDecimal(10.0)` ❌

**Always:**
- Use string constructor: `BigDecimal("1000")` ✅
- Verify currency before calculating ✅
- Convert user input (e.g., "10.00" USD) to cents (1000) at the edge of your system ✅

---

## Error Codes

> **Two distinct fields.** `PaymentResult.code` is the **gateway/response** code — `"100"` means the request
> reached the gateway successfully (**not** that the payment was approved). Whether the payment succeeded is in
> `transactionStatus` (`"SUCCESS"` / `"PROCESSING"` / `"FAILED"`). `isSuccess()` requires **both** `code == "100"`
> **and** `transactionStatus == "SUCCESS"`. The numeric codes below (`20x`–`31x`) arrive via `onFailure(PaymentError)`
> as communication/system errors; a declined card is **not** an error code — it comes back via `onSuccess` with
> `transactionStatus == "FAILED"`.

### Error Code Ranges

| Range | Category | Root Cause |
|---|---|---|
| 201–203 | **Initialization** | SDK not properly initialized, config invalid, context missing |
| 211–214 | **Connection State** | Not connected, connection lost, reconnect in progress |
| 231–232 | **App-to-App Mode** | Tapro app not installed, Intent not responded, permissions issue |
| 241–242 | **LAN Mode** | Device not found, network unreachable, DNS resolution failed |
| 251–255 | **Cable Mode** | USB device not found, serial port error, permission denied |
| 301–305 | **Transaction Parameter/Send Error** | Invalid amount, missing field, malformed request, send failed (retryable with same ID) |
| **306** | **Timeout** | No response from device or server (status unknown, must query) |
| 307–311 | **Transaction Failed** | Card declined, insufficient funds, auth failed, etc. (non-retryable without new ID) |

### Common Error Codes (detailed)

| Code | Name | Meaning | Action |
|---|---|---|---|
| 201 | INIT_ERROR | SDK initialization failed | Restart app, check credentials |
| 211 | NOT_CONNECTED | Attempted transaction without connection | Call `connect()` first |
| 306 | **TIMEOUT** | No response from terminal or server | **Query status before retrying** |
| 307 | TRANSACTION_FAILED | Card declined, insufficient funds, etc. | Retry with **new** `transactionRequestId` |
| 308 | TRANSACTION_REJECTED | Invalid amount, card expired, etc. | Retry with **new** `transactionRequestId` |

### Retry Rules

```kotlin
when (error.code) {
    "306" -> {
        // ⚠️ TIMEOUT — status unknown
        // MUST query before deciding to retry or show error
        queryTransactionStatus(error.transactionRequestId!!)
    }
    in listOf("301", "302", "303", "304", "305") -> {
        // Parameter/send error — safe to retry with same ID
        retryWithSameRequest()
    }
    in listOf("307", "308", "309", "310", "311") -> {
        // Transaction failed — must use NEW transactionRequestId
        retryWithNewId()
    }
    else -> {
        // Other errors — decide based on error category
        showError(error.message)
    }
}
```

---

## Callback & Thread Safety

### ConnectionListener

```kotlin
interface ConnectionListener {
    fun onConnected(deviceId: String, taproVersion: String)
    fun onDisconnected(reason: String)
    fun onError(error: ConnectionError)
}
```

### PaymentCallback

```kotlin
interface PaymentCallback {
    fun onProgress(event: PaymentEvent)
    fun onSuccess(result: PaymentResult)
    fun onFailure(error: PaymentError)
}
```

**PaymentEvent (progress states):**
- `"WAITING_CARD"` → "Please present card"
- `"WAITING_PIN"` → "Enter PIN on terminal"
- `"WAITING_SIGNATURE"` → "Please sign on terminal"
- `"WAITING_RESPONSE"` → "Contacting bank..."
- `"PRINTING"` → "Printing receipt..."
- `"COMPLETED"` → Operation complete
- `"CANCEL"` → User cancelled

### Thread Safety

**⚠️ Critical:** Callbacks may run on **background threads**. Always wrap UI updates:

```kotlin
override fun onSuccess(result: PaymentResult) {
    runOnUiThread {
        // Update UI here
        updateUI(result)
    }
}

override fun onProgress(event: PaymentEvent) {
    runOnUiThread {
        showProgressHint(event.eventCode)
    }
}
```

---

## Recovery & Idempotency

### transactionRequestId

- **Must be globally unique** per transaction attempt.
- Enable safe retry without duplicate charges.
- Recommended: UUID or timestamp-based.

```kotlin
val transactionRequestId = UUID.randomUUID().toString()
// or
val transactionRequestId = "txn_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
```

### Save before sending

```kotlin
// 1. Save transactionRequestId to DB first
val txnRecord = TransactionRecord(
    transactionRequestId = transactionRequestId,
    status = "PENDING",
    amount = amount,
    timestamp = System.currentTimeMillis()
)
database.insert(txnRecord)

// 2. Then send the transaction
client.sale(request, callback)

// 3. On response (success/failure/timeout), update the record
```

### Timeout Recovery (Error 306)

```kotlin
fun handleTimeout(transactionRequestId: String, attempt: Int = 1) {
    if (attempt > 12) {  // Max 12 retries × 5s = 60s
        showError("Transaction status unknown. Contact support.")
        return
    }

    val queryRequest = QueryRequest().setTransactionRequestId(transactionRequestId)
    client.query(queryRequest, object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            when (result.transactionStatus) {
                "SUCCESS" -> recordTransaction(result)
                "FAILED" -> showError("Transaction declined")
                "PROCESSING" -> {
                    // Still processing, poll again in 5s
                    Handler(Looper.getMainLooper()).postDelayed(
                        { handleTimeout(transactionRequestId, attempt + 1) },
                        5000
                    )
                }
            }
        }
        override fun onFailure(error: PaymentError) {
            // Query itself failed, retry
            Handler(Looper.getMainLooper()).postDelayed(
                { handleTimeout(transactionRequestId, attempt + 1) },
                5000
            )
        }
        override fun onProgress(event: PaymentEvent) {}
    })
}
```

---

## Tips

### On-screen tips (during sale)

```kotlin
val saleRequest = SaleRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(AmountInfo(
        orderAmount = BigDecimal("1000"),  // $10.00 (tip is NOT part of orderAmount)
        pricingCurrency = "USD"
    ))
    // tipConfig lives on the REQUEST (since v1.0.6), NOT inside AmountInfo:
    .setTipConfig(TipConfig(
        onScreenTip = true,                // Show tip prompts on the terminal
        tipMode = TipMode.ON_SALE,         // ON_SALE (during sale) or AFTER_SALE
        tipWithTax = false,                // Don't include tax in tip calculations
        suggestions = TipSuggestions(
            feeMode = FeeMode.RATE,        // RATE (percentage) or AMOUNT (fixed)
            values = listOf(15, 18, 20)    // 15%, 18%, 20%
        )
    ))
    .build()
```

### Post-auth tip adjustment

```kotlin
val tipRequest = TipAdjustRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId(originalSaleId)
    .setTipAmount(BigDecimal("200"))  // $2.00 tip in cents
    .build()

client.tipAdjust(tipRequest, callback)
```

### Tip exclusivity

- **Cannot set both `tipAmount` and `tipConfig`** — pick one.
- **`orderAmount` excludes tip** — always use the pre-tip sale amount.
- **Post-auth tip adjust** for settled (batch-closed) transactions.

---

## Security & PCI

### Credential Storage

```kotlin
// ✅ Correct: BuildConfig injection
val appId = BuildConfig.TAPLINK_APP_ID
val merchantId = BuildConfig.TAPLINK_MERCHANT_ID
val secretKey = BuildConfig.TAPLINK_SECRET_KEY

// ❌ Wrong: hardcoded
val appId = "my_app_id_12345"
```

**Inject via:**
- `local.properties` (local development, excluded from repo)
- Environment variables (CI/CD builds)
- Secure storage (e.g., Android Keystore)

### Card Data

**Store only:**
- Masked last 4 digits (e.g., `****1234`)
- Card brand (VISA, MASTERCARD, AMEX)

**Never store:**
- Full PAN (card number)
- Track 1 / Track 2 data
- CVV or CVC
- Expiry date (if not required by business)

### Production checklist

- [ ] `logEnabled = false` in Release builds
- [ ] Credentials from BuildConfig, never hardcoded
- [ ] Card data stored only as masked last4 + brand
- [ ] All amounts in cents (no floating-point)
- [ ] `disconnect()` + `removeConnectionListener()` in `onDestroy()`

---

## Official References

### SDK Artifact
- **Maven coordinate:** `com.sunmi:sunbay-taplink-sdk-android` (pin your target version)
- **Package:** `com.sunmi.tapro.taplink.sdk.*`
- **Key classes to reference (inspect via your IDE):**
  - `TaplinkSDK` (main API)
  - `TaplinkClient` (transaction methods)
  - `ConnectionConfig`, `TaplinkConfig` (setup)
  - `SaleRequest`, `RefundRequest`, `VoidRequest`, etc. (requests)
  - `PaymentResult`, `PaymentError`, `ConnectionError` (responses)

### Documentation
- **Official Android SDK guide (authoritative):** https://docs.sunbay.us/en/sdk/client-side/android
- **Release notes (breaking changes):** linked from the docs site
- **API reference:** see the docs site

### Version History
- **Current version:** 1.0.8 (released, as of 2026-08-14)
- **SDK published as:** `com.sunmi:sunbay-taplink-sdk-android:1.0.8` (pin your target version)
- **See the release notes for breaking changes in your target version**

---

## Updating this snapshot

When the SDK changes (new field, new error code, breaking API change), update this file:

1. Check the official SDK source (`taplinksdk/lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/`)
2. Verify the change in the online docs (`sunbay-open-docs/src/content/en/sdk/client-side/android.mdx`)
3. Update the corresponding section below.
4. Update the "Last Updated" date at the top.
5. Commit with message: `docs: update protocol reference for SDK vX.Y.Z`


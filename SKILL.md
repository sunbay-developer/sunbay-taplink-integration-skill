---
name: integrating-taplink-sdk
description: Use when integrating TapLink SDK for Android POS applications into a merchant terminal app — adding sale/refund/void/auth flows with proper connection modes (App-to-App, LAN, Cable/USB), error handling, amount conversion, and transaction recovery.
---

# Integrating TapLink SDK for Android POS

## Overview

TapLink SDK is a **semi-integrated payment solution** for Android POS terminals, enabling communication between a merchant POS app and a SUNBAY Tapro terminal (or external payment devices) using three connection modes: **App-to-App (Intent)**, **LAN (Network)**, and **Cable (USB/Serial)**.

**Core principle — do not invent the SDK contract.** The single biggest failure when integrating TapLink is guessing at field names, amounts, request/response structures, or connection logic. The real contract — `transactionRequestId`, `AmountInfo` in smallest currency units (cents), `PaymentResult.isSuccess()`/`isProcessing()`/`isFailed()`, error codes 201–311 — **cannot be guessed**. The facts you need live in **`taplink-protocol-reference.md`** (next to this file) — but treat it as a **dated snapshot** and **verify against the official SDK source** on every use, especially before a production release.

## When to use this skill

- Integrating TapLink SDK into a new or existing Android POS app
- Implementing sale, refund, void, auth, post-auth, or batch-close flows
- Connecting via App-to-App (Intent), LAN (mDNS/network), or Cable (USB/serial)
- Adding transaction recovery logic (query-based, idempotency via `transactionRequestId`)
- Handling amount conversion (cents ↔ business units) and tip flows
- Testing in Sandbox mode before going live

**Not for:** Stripe, Square, Adyen, or other processor SDKs (different protocols).

## Step 0 — Verify the SDK version & protocol from official sources (do this FIRST)

`taplink-protocol-reference.md` is a **snapshot dated [2026-08-14]** that CAN go stale — TapLink SDK and the Tapro firmware update over time. Before trusting any field name, enum, error code, request shape, or class name:

1. **Check the official online docs (authoritative):** https://docs.sunbay.us/en/sdk/client-side/android — the published Android SDK guide is the source of truth for field names, enums, error codes, request shapes, and connection modes.
2. **Check the SDK artifact:** the published AAR `com.sunmi:sunbay-taplink-sdk-android` (inspect the bundled classes in `com.sunmi.tapro.taplink.sdk.*` via your IDE) for exact class/method signatures.
3. **Check the release notes** for breaking changes in your target SDK version (linked from the docs site).
4. **Reconcile**: if a live value differs from the snapshot, **the live value wins**. Note the diff and report it so the snapshot stays current.

If you have no network access, proceed on the snapshot but **flag every technical detail as unverified**. Never silently rely on the cache as if it were current, especially amounts, error codes, and connection modes.

### Checking SDK version

Check the SDK version you depend on in your app module's Gradle file (the version pinned on
`com.sunmi:sunbay-taplink-sdk-android`), or via `TaplinkSDK.getVersion()` at runtime:

```kotlin
// Example dependency (pin your target version):
implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.8")
```

Review the release notes for breaking changes in that version at https://docs.sunbay.us/en/sdk/client-side/android.

## ⚠️ Required-credential gate — prompt for `appId` + `merchantId` + `secretKey` FIRST (BLOCKING)

The `appId` and `secretKey` are **mandatory** to initialize the SDK; `merchantId` is **usually required** but is technically optional in the current SDK (`TaplinkConfig.merchantId` is nullable — "merchant-less" config since v1.0.7). Confirm with your onboarding whether merchant-less mode applies. The most common failure is:

> *"I wrote the code, it compiles and runs — but all payments fail at runtime."*

This is because placeholder credentials compile but don't authenticate with Tapro.

**Before writing any transaction code, explicitly prompt the user:**

> "Before integrating TapLink SDK — I need three real credentials from your Sunbay/Sunmi account onboarding:\n
> 1. **appId** — your app-level identifier (e.g., `"com.merchant.pos.appid"`)\n
> 2. **merchantId** — your merchant account ID (e.g., `"merchant_12345"`)\n
> 3. **secretKey** — your secret key for HMAC/encryption (e.g., `"sk_live_..."`)\n\n
> These are **not** placeholders; real values are required or every transaction will be rejected.\n
> For Sandbox testing, use your Sandbox credentials; switch to Production credentials when ready.\n
> Never hardcode or commit these values — use BuildConfig or environment injection."

Then bake those real values into your config (via `BuildConfig`, `local.properties`, or environment). **Do not proceed past this gate without them.**

- **Never proceed on placeholder values** like `<your_app_id>` or `YOUR_APP_ID_HERE`.
- These credentials are **app-level (per-integrator)** — one set per merchant POS app at onboarding, normally shared across all customers. Configure them **once**, not per-transaction.
- If testing, ask: do you have Sandbox credentials? Use those first to verify integration; then switch to Production credentials when ready for live merchants.

## Step 1 — Pick the integration topology (do this before any code)

```
Where does the POS app run?
│
├─ A: On the Tapro terminal itself?
│  └─→ [App-to-App via Intent] ✅ Simplest
│
└─ B: On a separate (external) POS device?
   │
   ├─ B1: Can you reach Tapro over LAN/network?
   │  └─→ [LAN via mDNS + WebSocket] ✅ Common for office networks
   │
   └─ B2: Must use USB/serial cable?
      └─→ [Cable via USB/Serial] ✅ Most reliable for mobile/offline
```

**One topology minimum.** If multiple deployments are needed (e.g., one café uses LAN, another uses USB), design and test all of them now — the transport is pluggable from day one.

### Mode A: App-to-App (Intent) — On-terminal POS

```kotlin
val config = ConnectionConfig.createAppMode()
TaplinkSDK.connect(config, listener)
```

**Pros:**
- Simplest — Tapro already on the device, Intent-based.
- No network configuration.

**Cons:**
- Only works if POS app and Tapro are on the same device.
- If Tapro is not installed, connection fails.

**Check first:** Is the host project already running on the Tapro terminal? Confirm by reading the manifest and understanding the deployment model.

### Mode B1: LAN (Network via mDNS)

```kotlin
// First-time setup: obtain the terminal's LAN address, then connect.
// discoverLanServices / scanLanQrCode RETURN host/port via DiscoveryListener — they do NOT connect. (v1.0.8+)
TaplinkSDK.discoverLanServices(object : DiscoveryListener {   // one-shot mDNS (_taplink._tcp), 15s timeout
    override fun onDiscovered(services: List<DiscoveredService>) {
        val t = services.firstOrNull() ?: return
        TaplinkSDK.connect(ConnectionConfig.createLanMode(t.host, t.port), connectionListener)
    }
    override fun onError(error: ConnectionError) { /* E501/E503 ... */ }
})
// Or scan a lan://host/port QR shown on the terminal: TaplinkSDK.scanLanQrCode(discoveryListener)
// One-call variants that also connect: autoDiscoverAndConnect(listener) / scanAndConnect(listener)

// Subsequent connections: reuse the cached/known address
val config = ConnectionConfig.createLanMode()  // or createLanMode(host, port)
TaplinkSDK.connect(config, listener)
```

> There is **no** `DiscoveryConfig` / `startDiscovery` API. QR-scan APIs need CameraX + ZXing
> (`implementation` deps in your app module) and the `CAMERA` permission (declared by the SDK).

**Pros:**
- Works for POS apps on different devices (tablet + register).
- No cable needed.

**Cons:**
- Requires LAN connectivity and mDNS support.
- First setup requires QR code scanning (Tapro displays code, POS app scans it).
- Network latency and Wi-Fi reliability considerations.

**Check first:**
- Is the target deployment a networked office or mobile scenario?
- Can the POS app scan QR codes for pairing?
- Do you need device discovery logic, or is the Tapro IP/port hardcoded?

### Mode B2: Cable (USB/Serial)

```kotlin
val config = ConnectionConfig.createCableMode()
// or explicitly: ConnectionMode.CABLE + CableProtocol.AUTO (recommended)
TaplinkSDK.connect(config, listener)
```

**Pros:**
- Most reliable — no network dependency.
- Works offline.
- Direct USB or serial protocol.

**Cons:**
- Requires USB or serial hardware.
- Android 12+ needs runtime USB permissions.
- Protocol negotiation (VSP → RS232 → AOA) adds startup time.

**Check first:**
- What hardware is the Tapro device? (USB-capable? Which protocol?)
- What Android API level? (12+ needs runtime USB permission `android.permission.USB_HOST`).
- Is the POS app running on an Android device with USB/serial port?

## Step 2 — Inspect the host project and adapt to its architecture

This skill is **architecture-aware**. Instead of dumping boilerplate, do this:

1. **Understand the host codebase structure:**
   - Is it MVI? MVVM? MVP? Coroutines or RxJava?
   - How does it already do device I/O? (e.g., receipt printer, barcode scanner)
   - Where would payment logic naturally fit? (same layer as other device integrations, not the data/API layer)
   - How are settings/config stored? (SharedPreferences, Datastore, Room?)
   - How is dependency injection set up? (Hilt, Koin, manual?)

2. **Define a transport-agnostic interface:**
   ```kotlin
   interface PaymentTerminal {
       suspend fun sale(request: SaleRequest): PaymentResult
       suspend fun refund(request: RefundRequest): PaymentResult
       suspend fun void(request: VoidRequest): PaymentResult
       suspend fun query(transactionId: String): PaymentResult
       // ... other methods
   }
   ```
   UI and state layer depend **only** on this interface. All TapLink wire details hide inside the implementation.

   > **The SDK is callback-based.** `TaplinkClient.sale/refund/void/query(...)` deliver results through
   > `PaymentCallback` (`onSuccess`/`onFailure`/`onProgress`) on background threads. The `suspend` signatures
   > above are your **adapter's** surface — wrap the callback in `suspendCancellableCoroutine { ... }` (or expose
   > the raw callback API directly if the host app prefers callbacks/`Flow`). The Step 4 examples show the
   > underlying callback shape you bridge from. `onProgress` events have no coroutine equivalent — surface them
   > via a separate `Flow`/`LiveData` if the UI needs progress hints.

3. **One adapter per topology:**
   - `AppModePaymentTerminal` (App-to-App Intent)
   - `LanModePaymentTerminal` (Network mDNS)
   - `CableModePaymentTerminal` (USB/serial)

   All three implement `PaymentTerminal`. Pick which one(s) to use based on Step 1.

4. **Match the project's conventions:**
   - **State management:** If it uses ViewModel + LiveData, place payment state there. If it uses Redux or MVI, follow that pattern.
   - **Dependency injection:** If it uses Hilt, inject `PaymentTerminal` as a singleton. If it uses Koin, bind it there.
   - **Settings storage:** Where do the `appId`, `merchantId`, `secretKey` live? Same place as other app settings.
   - **Error handling:** Does the project use a centralized error dialog? Use that for payment errors.
   - **Logging:** Does it use Timber? Klog? Don't add a new logger — reuse the project's.
   - **Receipts:** If the app already builds receipts, add payment details to the receipt builder (card last4, auth code, timestamp).

5. **Update the schema:**
   - Extend any transaction/order record to store: `transactionId`, `transactionRequestId`, `cardLast4`, `cardBrand`, `authCode`, `transactionStatus` (SUCCESS / PROCESSING / FAILED).
   - For PCI compliance: **store only masked last4 + brand, never PAN or track data**.
   - Follow the project's DB migration mechanism (if using Room, add a migration).

## Step 3 — Initialize the SDK (once per app)

```kotlin
// Application.onCreate()
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig

override fun onCreate() {
    super.onCreate()

    // Use BuildConfig or environment-injected values, never hardcoded
    val config = TaplinkConfig(
        appId = BuildConfig.TAPLINK_APP_ID,
        merchantId = BuildConfig.TAPLINK_MERCHANT_ID,
        secretKey = BuildConfig.TAPLINK_SECRET_KEY
    )
    TaplinkSDK.init(this, config)
}
```

**Checklist:**
- [ ] Credentials come from `BuildConfig`, **not** hardcoded strings.
- [ ] `local.properties` or CI/CD injects real values (Sandbox or Production).
- [ ] `.gitignore` prevents committing `local.properties`.
- [ ] Logging is disabled in Release builds (`logEnabled = false`).

## Step 4 — Connect and execute transactions

### Connection Lifecycle

```kotlin
// Activity/ViewModel
class PaymentActivity : AppCompatActivity() {
    private val paymentTerminal: PaymentTerminal by inject() // Hilt / Koin

    override fun onResume() {
        super.onResume()
        if (!TaplinkSDK.isConnected()) {
            val config = when (deploymentMode) {
                "app-to-app" -> ConnectionConfig.createAppMode()
                "lan" -> ConnectionConfig.createLanMode()
                "cable" -> ConnectionConfig.createCableMode()
            }
            TaplinkSDK.connect(config, connectionListener)
        }
    }

    override fun onDestroy() {
        TaplinkSDK.disconnect()
        TaplinkSDK.removeConnectionListener()
        super.onDestroy()
    }
}
```

**Critical rules:**
- **Every `connect()` must have a matching `disconnect()` in `onDestroy()`.** Leaking connections wastes resources.
- **Connection can drop anytime.** Implement `onDisconnected()` and retry gracefully.
- **UI updates must run on the main thread.** If callback runs in a background thread, wrap UI calls in `runOnUiThread { }`.

### Sale (Charge)

```kotlin
val saleRequest = SaleRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")  // 6-32 chars
    .setTransactionRequestId(UUID.randomUUID().toString())      // Globally unique
    .setAmount(AmountInfo(
        orderAmount = BigDecimal("1000"),  // $10.00 in cents
        pricingCurrency = "USD"
    ))
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .setDescription("Product Purchase")
    .build()

paymentTerminal.sale(saleRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        when {
            result.isSuccess() -> {
                // ✅ Transaction approved
                saveTransactionRecord(result)
                showSuccess("Payment approved: ${result.transactionId}")
            }
            result.isProcessing() -> {
                // ⏳ Status unknown — query before deciding
                queryTransactionStatus(result.transactionRequestId!!)
            }
            result.isFailed() -> {
                // ❌ Transaction declined or failed
                showError("Payment failed: ${result.message}")
            }
        }
    }

    override fun onFailure(error: PaymentError) {
        // 🔴 Communication/system error (not a business rejection)
        when (error.code) {
            "306" -> {
                // Timeout — query for status, don't retry directly
                queryTransactionStatus(error.transactionRequestId!!)
            }
            "307", "308" -> {
                // Network error — retry with a NEW transactionRequestId
                retryWithNewId()
            }
            else -> showError("System error: ${error.message}")
        }
    }

    override fun onProgress(event: PaymentEvent) {
        runOnUiThread {
            when (event.eventCode) {
                "WAITING_CARD" -> showHint("Please present card...")
                "WAITING_PIN" -> showHint("Enter PIN on terminal...")
                "WAITING_SIGNATURE" -> showHint("Please sign on terminal...")
                "WAITING_RESPONSE" -> showHint("Contacting bank...")
                "PRINTING" -> showHint("Printing receipt...")
                else -> hideHint()
            }
        }
    }
})
```

### Refund

Two flavors: **referenced** (you have the original transaction ID) or **non-referenced** (card swipe required).

```kotlin
// Preferred: referenced (with original txn ID)
val refundRequest = RefundRequest.referencedBuilder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId(originalSaleTransactionId)
    .setAmount(AmountInfo(BigDecimal("500"), "USD"))
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .build()

paymentTerminal.refund(refundRequest, paymentCallback)

// Fallback: non-referenced (no original txn ID, requires new card swipe)
val refundRequest = RefundRequest.nonReferencedBuilder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setReferenceOrderId("REFUND_${System.currentTimeMillis()}")
    .setAmount(AmountInfo(BigDecimal("500"), "USD"))
    .build()
```

### Void (same-day reverse)

```kotlin
val voidRequest = VoidRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId(originalTransactionId)
    .build()

paymentTerminal.void(voidRequest, paymentCallback)
```

**Note:** Void only works for same-day, pre-settlement transactions. For settled (older) transactions, use Refund.

### Authorization (Pre-Auth)

```kotlin
val authRequest = AuthRequest.builder()
    .setReferenceOrderId("AUTH_${System.currentTimeMillis()}")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(AuthAmountInfo(orderAmount = BigDecimal("5000"), pricingCurrency = "USD"))
    .build()

paymentTerminal.auth(authRequest, paymentCallback)
```

### Post-Authorization (Complete Pre-Auth)

```kotlin
val postAuthRequest = PostAuthRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId(originalAuthTransactionId)
    .setAmount(AmountInfo(BigDecimal("4500"), "USD"))
    .build()

paymentTerminal.postAuth(postAuthRequest, paymentCallback)
```

### Query (Status Check & Timeout Recovery)

```kotlin
val queryRequest = QueryRequest().setTransactionRequestId(transactionRequestId)

paymentTerminal.query(queryRequest, object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        when (result.transactionStatus) {
            "SUCCESS" -> handleSuccess(result)
            "FAILED" -> handleFailure(result)
            "PROCESSING" -> {
                // Still processing — poll again after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed(
                    { query(transactionRequestId) },
                    5000
                )
            }
        }
    }

    override fun onFailure(error: PaymentError) {
        // Query itself failed — retry after delay
        Handler(Looper.getMainLooper()).postDelayed(
            { query(transactionRequestId) },
            5000
        )
    }

    override fun onProgress(event: PaymentEvent) {}
})
```

**Use cases:**
- **Error 306 (Timeout):** Always query before deciding to retry or show error.
- **Lost response:** Save `transactionRequestId` *before* sending; if no response, query to resolve true state.
- **User "Is it done yet?" check:** Query the terminal's view of the transaction.

## Step 5 — Go-Live Safety Checklist

Before releasing to production, verify **all** of these:

- [ ] **Credentials are real** — AppId, MerchantId, SecretKey from live production account (not Sandbox).
- [ ] **Logging is disabled** — `logEnabled = false` in production builds; Release builds enforce this.
- [ ] **Amounts are in cents** — All `orderAmount`, `tipAmount` use smallest currency unit (cents for USD/EUR/CNY).
- [ ] **`transactionRequestId` is globally unique** — Each transaction attempt gets a new UUID/timestamp.
- [ ] **`onSuccess` is interpreted correctly** — Code checks `isSuccess()` / `isProcessing()` / `isFailed()`, not just the method name.
- [ ] **Error 306 (Timeout) handling** — Before retrying, call `query()` to determine true state.
- [ ] **Error 307/308 handling** — Retry uses a **new** `transactionRequestId`, not the same one.
- [ ] **Connection cleanup** — `disconnect()` + `removeConnectionListener()` are called in `onDestroy()`.
- [ ] **UI thread safety** — All UI updates in callbacks wrapped in `runOnUiThread { }`.
- [ ] **No card data stored** — Only masked last4 + brand, never PAN/track/CVV.
- [ ] **USB permissions (Cable mode)** — Manifest has `android.permission.USB_HOST` + runtime permission check for Android 12+.
- [ ] **Error messages are user-friendly** — Technical error codes not shown; use user-facing messages.
- [ ] **Recovery tested** — Manually unplug/disconnect during a transaction and verify query/recovery logic works.
- [ ] **Multi-merchant tested** — If the app supports multiple merchants, each has their own credentials injected (no hardcoding).

---

## Common mistakes (learned from real integrations)

| Mistake | Reality |
|---|---|
| Treating `onSuccess` as "transaction approved" | `onSuccess` means "SDK executed successfully," but `transactionStatus` could still be PROCESSING or FAILED. Always check `result.isSuccess()` / `isProcessing()` / `isFailed()`. |
| Using integer cents on the wire | `orderAmount` is a `BigDecimal` in **major units** (e.g., `"1000"` for $10.00, not `10`). Never use `1000.0` (floating point) — use `BigDecimal("1000")` (string). |
| Auto-retrying on error 306 | 306 is timeout (status unknown). Must query first; only retry if query confirms a retry is safe. Auto-retrying without querying risks duplicate charges. |
| Reusing `transactionRequestId` for error 307/308 | 307/308 require a **new** ID. Reusing the same ID risks duplicate charges. |
| Not saving `transactionRequestId` before send | If the request hangs or times out, you need the ID to query status. Save it first in your DB, then send. |
| Omitting `disconnect()` in `onDestroy()` | Leaks connections; next activity's connection fails or is slow. Always call both `disconnect()` and `removeConnectionListener()`. |
| Querying on every error | Query only for genuine UNKNOWN (timeout 306, lost response). Don't query on real declines or cancels. |
| Running UI code in background callback threads | Crash or deadlock. Always `runOnUiThread { }` when updating UI from a callback. |
| Storing full PAN or track data | PCI-DSS violation. Store only masked last4 + card brand. |
| Hardcoding credentials | Code compiles; at runtime, SDK rejects bad credentials. Always use BuildConfig / environment injection. |
| Designing for one topology only, then needing another | If both on-app and LAN are needed, make transport pluggable from day one. |

## Red flags — STOP

- About to guess at a request/response field shape → read `taplink-protocol-reference.md` + check the official SDK first.
- Writing `orderAmount` as an integer or float → must be `BigDecimal("...")` in major units.
- "I'll handle error 306 by just retrying" → **STOP** — must query first.
- No `transactionRequestId` saved before sending the transaction → **STOP** — you won't be able to recover if it times out.
- Recording a payment before confirming `result.isSuccess()` → **STOP** — you might record a declined payment.
- Designing to support only one connection mode, but the customer needs another → rethink the transport layer now; it's much harder to add later.
- About to commit credentials (AppId, SecretKey) to the repo → **STOP** — use `local.properties` + `.gitignore`.

---

## Quick reference

| Task | Method | Idempotency |
|---|---|---|
| Charge a card | `sale(SaleRequest)` | Via `transactionRequestId` + `referenceOrderId` |
| Refund (with orig txn ID) | `refund(RefundRequest.referencedBuilder())` | Via `transactionRequestId` |
| Refund (no orig txn ID) | `refund(RefundRequest.nonReferencedBuilder())` | Via `transactionRequestId` |
| Reverse same-day charge | `void(VoidRequest)` | Via `transactionRequestId` + `originalTransactionId` |
| Pre-authorize | `auth(AuthRequest)` | Via `transactionRequestId` |
| Complete pre-auth | `postAuth(PostAuthRequest)` | Via `transactionRequestId` + `originalTransactionId` |
| Check status / recover | `query(QueryRequest)` | Via `transactionRequestId` |
| End-of-day settlement | `batchClose(BatchCloseRequest)` | Via `transactionRequestId` |
| Cancel in-progress txn | `abort(AbortRequest)` | Via `transactionRequestId` |

| Connection Mode | When to Use | Setup Time | Reliability |
|---|---|---|---|
| App-to-App (Intent) | POS on same device as Tapro | Fast | High (no network) |
| LAN (mDNS) | POS on different device, office network | Slow first time (QR scan), fast after | Medium (network dependent) |
| Cable (USB/Serial) | Offline, mobile, most reliable | Medium | High (direct connection) |

---

## Additional resources

- **Official docs (authoritative):** https://docs.sunbay.us/en/sdk/client-side/android
- **SDK artifact:** `com.sunmi:sunbay-taplink-sdk-android` (classes under `com.sunmi.tapro.taplink.sdk.*`)
- **Release notes & breaking changes:** linked from the docs site
- **Protocol reference (snapshot):** See `taplink-protocol-reference.md` (next to this file)


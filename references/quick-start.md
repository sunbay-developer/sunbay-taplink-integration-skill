# First payment: SDK 1.1.0 + Tapro 1.0.5

Start with the existing app's [project wiring](project-wiring.md). This page uses Kotlin DSL and Kotlin examples; for Java source or Groovy build files, use the matching [Java/Groovy guide](java-and-groovy.md) while retaining these dependency and payment requirements.

## 1. Resolve dependencies

Reuse the consumer's version catalog. Add repositories inside the existing `settings.gradle.kts` dependency resolution block:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // usb-serial-for-android, declared by the SDK POM
    }
}
```

App module:

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.1.0")
}
```

SDK source sets minSdk **24**, compileSdk **35** and public-module Java/JVM target **11**. Keep the app's compatible toolchain; avoid downgrading a modern project's Kotlin compiler. The consumer needs minSdk at least 24 and compatible Java/Android build settings.

**Check runtime dependencies, especially for a bare app.** The verified 1.1.0 POM lists Java-WebSocket 1.5.3 and usb-serial-for-android 3.7.0. SDK source also uses AndroidX and coroutines. Reuse compatible app versions; add missing libraries. Source baseline versions:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
}
```

For built-in QR scanning, add CameraX `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` **1.3.4**, and `com.google.zxing:core:3.5.3`; see [connections](connections.md). SDK 1.1.0 JSON handling needs no additional Jackson/Gson dependency. A manually copied AAR needs its runtime dependencies supplied by the app.

Inspect the merged manifest: network, camera and USB declarations come from the AAR. Register the app's own Application class. For non-USB distributions, assess merged USB feature requirements so device filtering matches deployment. Retain AAR consumer rules and smoke-test the minified app.

## 2. Configure credentials locally

`appId` and `secretKey` are required for authenticated use. `merchantId` is optional for local SDK initialization; the connected Tapro supplies its provisioned merchant context. Confirm environment and app/API permissions through onboarding. Local modes have no SDK `sandbox = true` switch: use the correct Tapro environment and corresponding app credentials.

For onboarding, test instruments and Sandbox/go-live preparation, follow [official documentation lookup](documentation-lookup.md). Select documentation language from the conversation; Sandbox payment testing still uses the public documentation site by default. Keep the application's pinned SDK version while resolving those setup details.

Reuse existing secret provisioning. For local development, add `local.secrets.properties` to `.gitignore` **before** creating it:

```properties
# Fill on the developer machine; keep this file out of Git.
TAPLINK_APP_ID=
TAPLINK_SECRET_KEY=
TAPLINK_MERCHANT_ID=
```

App `build.gradle.kts` (import at the top):

```kotlin
import java.util.Properties

val taplinkSecrets = Properties().apply {
    val file = rootProject.file("local.secrets.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun taplinkLiteral(key: String): String {
    val value = providers.environmentVariable(key).orNull
        ?: taplinkSecrets.getProperty(key, "")
    return "\"" + value.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "TAPLINK_APP_ID", taplinkLiteral("TAPLINK_APP_ID"))
        buildConfigField("String", "TAPLINK_SECRET_KEY", taplinkLiteral("TAPLINK_SECRET_KEY"))
        buildConfigField("String", "TAPLINK_MERCHANT_ID", taplinkLiteral("TAPLINK_MERCHANT_ID"))
    }
}
```

Empty values permit compilation. At runtime show configuration-required state and disable connection/payment until provisioned. Keep the config object and keys out of logs. BuildConfig values remain extractable from the APK; use approved production provisioning/storage.

## 3. Initialize once

In the existing `Application.onCreate()`, after credentials are available. Use the **consumer app's** BuildConfig.

```kotlin
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig

if (BuildConfig.TAPLINK_APP_ID.isNotBlank() && BuildConfig.TAPLINK_SECRET_KEY.isNotBlank()) {
    TaplinkSDK.init(
        this,
        TaplinkConfig(
            appId = BuildConfig.TAPLINK_APP_ID,
            secretKey = BuildConfig.TAPLINK_SECRET_KEY,
            merchantId = BuildConfig.TAPLINK_MERCHANT_ID.takeIf { it.isNotBlank() },
            timeout = 180, // seconds
            logEnabled = false
        )
    )
}
```

Always retain the value returned by `TaplinkConfig` setters, or construct a fresh config as above. The public tag returns copies while the verified Maven AAR mutates and returns the same instance; [the baseline records this difference](source-baseline.md#resolve-conflicting-examples). Avoid relying on either mutation or shared-instance behavior. Its zero-argument constructor is unavailable in 1.1.0. Java callers use `TaplinkConfig.create(appId, secretKey)` and `TaplinkSDK.initSDK(context, config)`.

## 4. Connect, send, recover

Choose the asset matching existing source: [Kotlin](../assets/TaplinkQuickStart.kt) or [Java](../assets/TaplinkQuickStartJava.java). Both include complete public imports. Implement the journal using the app's existing order repository and map updates to its UI; complete the concrete seams in [project wiring](project-wiring.md#make-the-app-wiring-concrete). The wiring below is Kotlin.

The journal must atomically reserve the order, reject duplicate/unresolved submissions and persist request ID, order, amount/currency and terminal association **before transmission**. Its methods must be thread-safe. Run synchronous journal work on the app's IO dispatcher. Storage schema and order settlement remain app-owned.

```kotlin
// One instance per payment owner, created after SDK initialization.
val session = TaplinkQuickStart(journal, paymentIoExecutor) { update ->
    renderPaymentUpdate(update) // main thread
}
session.connect(ConnectionConfig.createOnDeviceMode())

// After connection AND getTerminalInfo identity verification, on an explicit checkout
// action and the app's IO dispatcher (see the identity procedure below):
val request = TaplinkQuickStart.saleRequest(
    orderId = "ORDER_123456", // 6–32 characters
    requestId = UUID.randomUUID().toString(),
    amountCents = 1000L,
    currency = "USD"
)
session.sale(request)

// After reconnect and verification against the original attempt's saved terminal identity:
// recovery uses the original ID read from durable order storage.
session.query(originalRequestId)
```

Wiring imports: `java.util.UUID`, `com.sunmi.tapro.taplink.sdk.config.ConnectionConfig`. `journal`, `renderPaymentUpdate` and `originalRequestId` belong to the app. `paymentIoExecutor` is an app-owned background `java.util.concurrent.Executor` (for example, a shared single-thread executor); keep it alive until queued journal writes complete. The Kotlin asset is the compilable API sample. Journal failures must reach the app's storage-error handling; keep the pending record and payment controls locked until reconciled.

Enable payment after connection **and terminal identity verification**: call `getTerminalInfo()`, verify the selected/provisioned SN and merchant context, then persist the accepted identity/endpoint. On first enrollment the operator verifies the physical terminal serial. On recovery compare metadata with the original attempt's saved identity before query; missing/mismatched identity keeps payment controls disabled. See [the identity procedure](connections.md#lan-discovery-and-selection) and [the metadata callback](../taplink-protocol-reference.md#read-terminal-metadata). `onConnected(deviceId, taproVersion)` may initially carry placeholders. The first payment operation performs INIT automatically; metadata verification and transaction permission checks are separate steps.

Handle `Update.Unresolved` with query on the same terminal/session, following [bounded recovery](recovery.md). **Both `Unresolved` and `TechnicalFailure` keep the order locked** until reconciliation or an explicit business decision based on a confirmed non-payment outcome. For a technical failure, triage its cause before choosing recovery; fixing 303 configuration alone does not resolve any earlier unknown attempt. Every query failure remains unresolved. An error/disconnect leaves the durable record available. Approved/declined updates belong to the captured operation ID even if the UI navigates elsewhere.

## 5. Lifecycle and validation

Call `session.close()` at the payment owner's lifecycle end; it calls both SDK cleanup methods. If a service owns the singleton connection, the service owns cleanup and Activities detach their UI subscriptions. Avoid closing a shared connection on transient screen rotation. Preserve pending records across process death; reconnect and query before another payment for those orders.

Run the actual app variant: `./gradlew :app:compileDebugKotlin` for Kotlin or `./gradlew :app:compileDebugJavaWithJavac` for Java in an unflavored app. Inspect `:app:dependencies --configuration debugRuntimeClasspath` (adapt flavors). Then run [device scenarios](recovery.md#integration-verification). Compilation checks signatures; device tests verify transport, initialization, merchant permissions and payment behavior.

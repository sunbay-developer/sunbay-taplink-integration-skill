# Java source and Groovy Gradle

Use when the consumer's existing source or Gradle DSL calls for it. Keep the project's source language and DSL; a Java app can use Kotlin Gradle DSL and a Kotlin app can use Groovy.

## Java API differences at 1.1.0

Adapt [TaplinkQuickStartJava.java](../assets/TaplinkQuickStartJava.java), compiled with Java 11 against the actual 1.1.0 AAR. A pure Java app needs the SDK's Kotlin runtime libraries, but it does not need a Kotlin source plugin merely to consume the AAR. Reuse resolved compatible versions; add `org.jetbrains.kotlin:kotlin-stdlib:1.7.10` if absent, plus required AndroidX/coroutines from [quick start](quick-start.md).

| Purpose | Java form |
|---|---|
| Initialize | `TaplinkSDK.initSDK(context, TaplinkConfig.create(appId, secretKey))` |
| Initialization / connection status | `isSDKInitialized()` / `isSDKConnected()` |
| Connect / disconnect | `connectSDK(config, listener)` / `disconnectSDK()` |
| Cleanup listener | `removeSDKConnectionListener()` |
| Payment client | `TaplinkSDK.getClient()` |
| Connection factories | `ConnectionConfig.Companion.createOnDeviceMode(AppToAppMode.CUSTOM)`; Cross-Device factory takes all four parameters `(strategy, host, port, cableProtocol)` |
| SALE builder | `new SaleRequest.Builder()` |
| Card method | `new PaymentMethodInfo(PaymentCategory.CARD, null, null)` |
| Original-ID query | `new QueryRequest().setTransactionRequestId(originalId)` |
| Callbacks | Extend `ConnectionListenerAdapter` / `PaymentCallbackAdapter`, then implement relevant handlers; direct `ConnectionListener` also requires `onReconnecting(int, int)` |

The Kotlin factory's default On-Device argument is `AppToAppMode.CUSTOM`; Java supplies it explicitly. That enum describes local app interaction, independently of LAN/Cable protocol selection.

`ConnectionConfig.getConnectionMode()` exposes normalized transport values: On-Device resolves to `APP_TO_APP`, and Cross-Device to `LAN` or `CABLE`. Use the selected factory/strategy for the integration choice; avoid requiring the getter to return the higher-level selection name.

## Startup and owner wiring

Call `TaplinkQuickStartJava.initialize(context, appId, secretKey, merchantId)` from the existing Application after provisioning. It returns false for missing app ID/key so the app can show configuration-required state. Logging is disabled. For an already initialized singleton, retain the existing initialization owner/configuration.

Create one `TaplinkQuickStartJava(journal, paymentIoExecutor, observer)` in the existing payment owner. Implement its `Journal` by delegating to the existing durable store; the [project-wiring table](project-wiring.md#make-the-app-wiring-concrete) describes the semantics. Connect with `onDevice()`, `lan(host, port)`, `cable(protocol)` or `subScreen()`.

`CONNECTED` triggers identity verification. After readiness, create `saleRequest(orderId, amountCents, currency)` and invoke `sale(request)` on the app's IO dispatcher. `query(originalId)` sends one query; the owner binds it to saved terminal identity and bounds polling. Observer updates run on the main thread. Both `UNRESOLVED` and `STORAGE_ERROR` retain pending state; inspect the cause rather than resending SALE. `close()` disconnects and removes the listener.

## Terminal identity

This method belongs in the existing payment owner. Supply current-connection callbacks that compare metadata with the saved SN/merchant before enabling checkout/query. Dispatch UI updates to main; ignore responses from older connections. The SDK call itself performs no comparison.

```java
import com.sunmi.tapro.taplink.sdk.TaplinkSDK;
import com.sunmi.tapro.taplink.sdk.callback.TerminalInfoCallback;
import com.sunmi.tapro.taplink.sdk.error.PaymentError;
import com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo;
import java.util.function.Consumer;

static void readTerminalIdentity(Consumer<TerminalInfo> onInfo, Consumer<PaymentError> onError) {
    TaplinkSDK.getClient().getTerminalInfo(new TerminalInfoCallback() {
        @Override public void onSuccess(TerminalInfo info) { onInfo.accept(info); }
        @Override public void onFailure(PaymentError error) { onError.accept(error); }
    });
}
```

Read `info.getTerminal().getSn()` and `info.getMerchantId()`. For APPtoAPP, verify and persist same-device metadata automatically; Pay needs no extra confirmation screen. Ask for selection only when the target is ambiguous. Recovery compares with the original attempt; missing/mismatched metadata leaves a visible reason beside Pay. See [identity by mode](connections.md#terminal-identity-by-mode). `onConnected` arguments may be placeholders before INIT.

## Groovy dependency and credential injection

For `settings.gradle`, merge repositories into its existing resolution policy:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

In the app's `build.gradle`:

```groovy
dependencies {
    implementation 'com.sunmi:sunbay-taplink-sdk-android:1.1.0'
}
```

Reuse a version catalog if present. Add optional scanner libraries only for QR scanning. Use [quick start](quick-start.md) for runtime dependencies, minimum Android API and credential requirements.

For local-development BuildConfig injection, first ignore `local.secrets.properties` in Git. Keep blank keys in that local file until provisioned. Place imports at the top of the existing build file, then merge the rest:

```groovy
import java.util.Properties
import groovy.json.JsonOutput

def taplinkSecrets = new Properties()
def taplinkSecretsFile = rootProject.file('local.secrets.properties')
if (taplinkSecretsFile.isFile()) {
    taplinkSecretsFile.withInputStream { taplinkSecrets.load(it) }
}
def taplinkLiteral = { String key ->
    String value = providers.environmentVariable(key).getOrNull()
    if (value == null) value = taplinkSecrets.getProperty(key, '')
    JsonOutput.toJson(value)
}
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField 'String', 'TAPLINK_APP_ID', taplinkLiteral('TAPLINK_APP_ID')
        buildConfigField 'String', 'TAPLINK_SECRET_KEY', taplinkLiteral('TAPLINK_SECRET_KEY')
        buildConfigField 'String', 'TAPLINK_MERCHANT_ID', taplinkLiteral('TAPLINK_MERCHANT_ID')
    }
}
```

Use the consumer's generated BuildConfig fields when initializing. Empty values allow compilation and leave runtime connection disabled. BuildConfig embeds values in the APK; production provisioning/storage follows the deployment's approved approach. Preserve existing credential injection if it already solves this requirement.

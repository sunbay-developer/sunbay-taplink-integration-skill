# Source baseline and maintenance

## Pinned evidence

Reviewed **2026-11-04**. This skill is a versioned integration reference, not a claim that a device/backend test suite has passed.

| Component | Version/ref | Exact source |
|---|---|---|
| Public Android SDK | `v1.1.0` | `762abc2fa0355c46e33636fdf10a339d2e22c103` |
| SDK binary used for example compilation | Maven `com.sunmi:sunbay-taplink-sdk-android:1.1.0` | AAR BuildConfig `VERSION_NAME=1.1.0`, `VERSION_CODE=18` |
| Tapro Host | Local `release_1.0.5` | `9852b5d7d6cc4f8a9e6cb86380400155a1327174` |

SDK source: [fixed public revision](https://github.com/sunbay-developer/taplink-sdk-android/tree/762abc2fa0355c46e33636fdf10a339d2e22c103). [Published Maven POM](https://repo.maven.apache.org/maven2/com/sunmi/sunbay-taplink-sdk-android/1.1.0/sunbay-taplink-sdk-android-1.1.0.pom). The cached AAR matches Maven Central's published SHA-1; its SHA-256 is `044694ecabbdcfb9a59c4b92be7850ec4f166160c9a670aed5b99a5f5d3c8f14`.

The Tapro ref is the local 1.0.5 release branch, not the workspace's current develop branch or a future multi-terminal feature branch. Private remote refresh failed due to missing Git authentication; the remote tip was not reverified. Tie compatibility conclusions to the exact Host commit/build above. Integrators can use the packaged references without accessing that private repository.

Tapro's own `gradle/libs.versions.toml` at this commit references `1.0.9.2-SNAPSHOT` for its internal SDK dependency. The **integrator's** dependency in this task is 1.1.0. These are separate build inputs; the source review does not claim that Tapro itself was rebuilt with SDK 1.1.0 or that every distributed 1.0.5 APK has this commit's changes.

## Source map

Paths below are repository-relative. For SDK rows, prefix with `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/` unless another root is shown. Read implementation and type declarations; some KDoc/README fragments still describe older behavior.

| Source | Verified facts used by the skill |
|---|---|
| `TaplinkSDK.kt`, `TaplinkClient.kt`, `api/TaplinkApi.kt` | Root-package entry points, callback operations, getClient, cleanup, terminal info |
| `config/TaplinkConfig.kt` | Required appId/secretKey, optional merchantId, seconds timeout 180; setter mutation differs in Maven AAR, see below |
| `config/ConnectionConfig.kt`, `enums/CableProtocol.kt`, `enums/CrossDeviceStrategy.kt` | Explicit LAN/Cable/protocol selection, On-Device/Sub-Screen, deprecated factories, autoReconnect=false |
| `model/common/AmountInfo.kt`, `model/request/transaction/AuthAmountInfo.kt` | Required constructors/factories and smallest currency units |
| `model/request/transaction/*.kt`, `model/request/transaction/settlement/BatchCloseRequest.kt` | Typed builders, original-reference fields, refund builder differences, abort signature, receipt choices |
| `model/request/transaction/TransactionRequestValidator.kt` | Order-reference constraints, amount validation, tip mutual exclusion |
| `model/request/QueryRequest.kt`, `model/response/PaymentResult.kt` | Actual query fields and result helper predicates |
| `model/response/TerminalInfo.kt`, `callback/TerminalInfoCallback.kt` | Connected merchant/terminal metadata, no owner/availability fields |
| `manager/PaymentManager.kt`, `protocol/ProtocolRequestBuilder.kt`, `RELEASE-NOTES.md` | Timeout unit resolution, On-Device callback exception, effective signed/local timeout |
| `error/PaymentError.kt` | Nullable request identity and limited ID-retry hint |
| `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/enums/InnerErrorCode.kt` | Error meanings, especially 303/306/307/308/312 |
| `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/cable/` | USB device/accessory permission calls; transport internals are source evidence, not consumer imports |
| `lib_taplink_sdk/build.gradle.kts`, `gradle/libs.versions.toml`, both module manifests, 1.1.0 POM | Version 1.1.0/code 18, minSdk 24, public JVM target 11, runtime/QR dependencies, manifest merging |

Tapro paths use `lib_taplink_service/src/main/java/com/sunmi/tapro/taplink/service/` unless fully qualified:

| Source | Host facts used by the skill |
|---|---|
| `message/MessageManager.kt`, `message/InitializationManager.kt` | Deferred INIT, active-flow/busy rejection, control dispatch |
| `message/TaplinkApiPermissionManager.kt`, `message/RequestSignatureVerifier.kt` | Mode/action permission checks and authentication; transport connection does not approve a transaction |
| `message/MessageManager.kt`, `TerminalInfoProvider.kt` | GET_TERMINAL_INFO uses current merchant/terminal data, can run during payment |
| `message/MessageManager.kt`, `message/RequestTimeoutCleanupCoordinator.kt` | LAN/Cable cancellability and request-timeout cleanup |
| `message/BackgroundTransactionHandler.kt` | QR void rejection; specialized background routing |
| `biz_taplink/src/main/java/com/sunmi/tapro/taplink/dispatch/DispatchViewModel.kt` | Transaction routing, timeout propagation and screen-control handling |
| `biz_taplink/src/main/java/com/sunmi/tapro/taplink/repository/impl/TaplinkConfigurationRepositoryImpl.kt` | Request-to-Host mapping including amount/tip behavior |
| `lib_posp/src/main/java/com/sunmi/tapro/posp/bean/request/transaction/PaymentCardSaleRequest.kt` | No allowTipAdjustment field at pinned 1.0.5 tip |

## Resolve conflicting examples

**Observed source/binary difference:** `TaplinkConfig.setTimeout()` in public tag `v1.1.0` uses `copy(...)`, but the Maven Central AAR identified above mutates `timeout` and returns the same instance. A JVM check initially failed the source-derived copy expectation; direct execution and bytecode inspection confirmed the difference. The baseline has source and binary evidence, not an assumption of byte-for-byte equivalence. Integration guidance uses a fresh constructor or retains setter return values, which works with both forms. Do not rely on a shared config instance remaining unchanged. Default timeout 180 seconds and optional local merchantId are confirmed by both.

At review time, online/source examples still included zero-argument `TaplinkConfig()`/`AmountInfo()`, an older README badge, or older automatic connection commentary. The 1.1.0 public classes and artifact supply the actual signatures. The skill uses required constructors/factories and explicit enums, and avoids blanket “live docs always override a pinned artifact” rules.

The old skill also used no-argument `createCableMode()` and `setPaymentMethod` on a referenced refund builder; both fail compilation against the 1.1.0 AAR. These are replaced by explicit protocol selection and the typed refund recipe.

Public context: [Android SDK guide](https://docs.sunbay.us/en/sdk/client-side/android), [SDK release notes at this revision](https://github.com/sunbay-developer/taplink-sdk-android/blob/762abc2fa0355c46e33636fdf10a339d2e22c103/RELEASE-NOTES.md). Android platform permission guidance: [USB host](https://developer.android.com/develop/connectivity/usb/host).

## Maintaining a new baseline

1. Record the target release ref, immutable commit and actual artifact version. Inspect the intended Tapro release branch rather than a newer workspace checkout.
2. Recheck the public types and corresponding Host handling. Keep facts from implementation distinct from proposed behavior and device-validated capabilities.
3. Update only affected references and examples. Keep offline use practical; do not require every integrator to clone internal repositories or provide secrets before code generation.
4. Compile all Kotlin assets against the real AAR and Android SDK classpath. Exercise builders and result helpers. Validate local links and skill metadata.
5. Run a fresh-context integration scenario from [recovery](recovery.md#integration-verification), including missing credentials and unknown outcomes. Record source checks, compiler checks and any actual device checks separately.

SDK/Tapro source changes, publishing, dependency upgrades in other repositories and real payment tests follow their own authorization and release processes.

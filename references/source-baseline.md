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

## SDK-local llms.txt

Updated locally on **2026-11-04** to **SDK 1.1.0 + Tapro release_1.0.5**. Repository-relative path: `llms.txt`; revised working-tree Git blob: `4c66101bf61d5066add4b272cf0ef531ffa1c5b2`. Four Kotlin files extracted directly from the revised document compile against the published 1.1.0 AAR; 23 JVM model checks pass. Device/payment validation remains separate. Maven Central metadata also reported latest/release `1.1.0` on this date.

The historical copy reviewed before this update came from SDK checkout `c96f16012d3193da3ff173da393ee130760c3a0b`, Git blob `a0c3194e55e50f2fe3c5a8fc431d4c4e0218c1d8`, and declared **1.0.9**. The pinned public `v1.1.0` tree has no `llms.txt` entry; the local document update does not change that tag. The table below records corrections for consumers who still have the historical copy.

| Historical 1.0.9-copy content | Corrected 1.1.0 integration guidance |
|---|---|
| `implementation(project(":lib_taplink_sdk"))` | External apps use Maven `com.sunmi:sunbay-taplink-sdk-android:1.1.0`; see [quick start](quick-start.md) |
| `CrossDeviceStrategy.AUTO`, `CableProtocol.AUTO`, implicit Cable protocol | Select LAN or CABLE explicitly; select `USB_VSP`, `RS232` or `USB_AOA` for Cable; see [connections](connections.md) |
| Success code `"0"`; failure inferred from a nonzero code | Call result predicates: `isSuccess()` checks `"100"` and `"SUCCESS"`, `isProcessing()` checks `"PROCESSING"`, `isFailed()` checks `"FAILED"`; preserve an unknown-state branch |
| `result.transactionRequestId!!` / `error.transactionRequestId!!` | Capture and persist the original request ID before sending; use it when callback IDs are absent |
| `308` immediately creates a new transaction; generic `canRetryWithSameId` replay | Query the original operation and resolve its outcome before an authorized new attempt; see [recovery](recovery.md) |

When this file is supplied, read it and check for drift before reusing examples. Its availability does not establish the availability of the website's language-specific index. External integrators can use the packaged references without obtaining the maintainer's checkout.

## Documentation workflow reference

The documentation lookup guidance was informed by [sunbay-integration-skill at commit 6160c300f4f1270679562d84c5a29779f848e6ad](https://github.com/sunbay-developer/sunbay-integration-skill/blob/6160c300f4f1270679562d84c5a29779f848e6ad/SKILL.md), reviewed on 2026-11-04. Its useful patterns are language-aware navigation, distinct page/Markdown/schema roles, registry/source verification and preservation of an already-pinned dependency.

For this Android skill, those discovery patterns accompany the versioned SDK/Host evidence and compiled examples. Public production indexes `/zh/llms-md.txt` and `/en/llms-md.txt` returned HTTP 404 during the check; both Android guide pages and `/zh/docs` returned 200. The [lookup reference](documentation-lookup.md) therefore includes verified-page and navigation fallbacks. These HTTP results are observations on the review date, not a permanent availability rule. The external skill informs the documentation workflow; SDK/Tapro facts remain grounded in the sources listed above.

## Integration workflow comparisons

Reviewed **2026-11-04**. These official ecosystem examples inform the skill's usability; payment/API behavior still follows the pinned TapLink sources above.

| Reference | Pattern applied here |
|---|---|
| [Stripe best practices](https://github.com/stripe/ai/blob/8f4c6295246b771bdfbffd4a50171f75a03507c8/skills/stripe-best-practices/SKILL.md) | Route by requested integration task; load only relevant references and retain critical payment requirements |
| [Vercel AI SDK](https://github.com/vercel/ai/blob/6adf8c797d5e2bf056c077f946aa12dabb3cb268/skills/use-ai-sdk/SKILL.md) | Inspect the actual target package/module and version, verify signatures and compile after editing |
| [RevenueCat Android configuration](https://github.com/RevenueCat/play-billing-skills/blob/8fa2e4c25d4171be5950888b3ce89d500f6e5cfa/revenuecat/configuring-the-sdk/SKILL.md) | Find existing initialization and Gradle wiring first; adapt the app's language/DSL and verify a small integration path |
| [skills CLI](https://github.com/vercel-labs/skills/blob/9197d919034d19010ae303cf96bf19ad087dd4be/docs/commands.md) and [Agent Skills specification](https://agentskills.io/specification) | One-command installation, installed folder matching the skill name, complete resources and progressive disclosure |

A Java baseline application exercise compiled successfully using the previous skill, but required translating Kotlin factories/static entry points and reading several references. It exposed missing Java examples and a need for concrete existing-store/terminal-identity wiring. The revised package adds a compiled Java adapter and Java/Groovy guide, targeted wiring instructions, direct installation and a clean offline ZIP. It preserves the pinned SDK, selected consumer architecture and local credential handling. No elapsed-time reduction or device-payment success is inferred from these checks.

### Usability validation

Validation on 2026-11-04 used the actual 1.1.0 AAR and isolated consumer fixtures:

- `skills` CLI **1.7.6** discovered and copied the skill into a temporary Codex project; ZIP extraction produced the same canonical `taplink-integration/` layout.
- Both Kotlin assets and the Java adapter compiled against SDK 1.1.0 / Android 35. The Java terminal-info snippet also compiled. **20** existing Kotlin/JVM model checks and **6** Java request/connection checks passed.
- The exact Groovy credential snippet configured a temporary Gradle DSL fixture with empty and escaped string values (**2** cases). This verifies the snippet's syntax/injection, separately from a real consumer's AGP build.
- **5** packaging tests covered complete resources, exclusion of local state, invalid names, symlinks and missing links without overwriting a previous delivery.
- Fresh-context Java first-integration code used the distributed Java asset and compiled while preserving the fixture's existing store interface. Application-specific pending-record/terminal-binding reads remain explicit wiring points.
- A separate existing-callback scenario changed recovery without replacing initialization/storage/language. Its **7** local checks passed after removing 306 SALE replay and using the captured original ID for 306/308 and unresolved results.

These are installation, compilation, model and application-exercise checks. Real terminal, backend, merchant/processor permissions and payment acceptance remain deployment checks. No time-to-integrate guarantee is derived from the exercises.

## Maintaining a new baseline

1. Record the target release ref, immutable commit and actual artifact version. Inspect the intended Tapro release branch rather than a newer workspace checkout.
2. Recheck the public types and corresponding Host handling. Keep facts from implementation distinct from proposed behavior and device-validated capabilities.
3. Update only affected references and examples. Keep offline use practical; do not require every integrator to clone internal repositories or provide secrets before code generation.
4. Compile Kotlin and Java assets against the real AAR and Android SDK classpath. Exercise builders and result helpers. Validate local links and skill metadata.
5. Run a fresh-context integration scenario from [recovery](recovery.md#integration-verification), including missing credentials and unknown outcomes. Record source checks, compiler checks and any actual device checks separately.

SDK/Tapro source changes, publishing, dependency upgrades in other repositories and real payment tests follow their own authorization and release processes.

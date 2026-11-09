# Fit TapLink into the existing app

Use for mode integration, first payment or a major adaptation. For a single failing callback or one new transaction type, start at the relevant existing owner instead.

## Find the integration points

Start with file names and symbol locations; avoid dumping configuration or credential values:

```bash
rg --files -g '*gradle*' -g '*.toml' -g 'AndroidManifest.xml' -g '!**/build/**' -g '!**/.gradle/**'
rg -l 'sunbay-taplink-sdk-android|taplink|\.aar|fileTree|project\(' \
  -g '*gradle*' -g '*.toml' -g '!**/build/**' -g '!**/.gradle/**'
rg -l 'TaplinkSDK|TaplinkClient|PaymentService|OrderRepository|RoomDatabase|SQLiteOpenHelper' \
  -g '*.kt' -g '*.java' -g '!**/build/**'
rg -l 'class .*Application|android:name=' -g '*.kt' -g '*.java' -g 'AndroidManifest.xml' -g '!**/build/**'
```

Read the relevant files and record the resolved values briefly:

| Choice | Infer from | Action |
|---|---|---|
| Module and variant | settings, Android application plugin, flavors, existing build tasks | Edit and compile the module that owns checkout |
| Language and DSL | Existing payment classes and module build file | Keep Java/Kotlin and Groovy/Kotlin DSL; these are independent choices |
| SDK version | Direct dependency, consumed catalog alias, convention plugin, shared module or local AAR | Trace the target module's dependency; preserve selected version; use 1.1.0 for a new unpinned integration |
| Initialization owner | Existing Application/service/payment manager | Extend the current owner and initialize once |
| Durable storage | Order/attempt repository and transaction boundaries | Implement the journal contract against it |
| Mode | User's requested mode/device arrangement and current connection settings | APPtoAPP / App-to-App / APP_TO_APP / same device → On-Device; separate terminal → explicit LAN/Cable; dedicated display → Sub-Screen |
| Credentials | Existing provisioning or config field names | Reuse injection; keep values out of chat/output |

Ask only when a choice remains unresolved after inspection. An APPtoAPP request already specifies the same-device route; confirm unspecified [payment functions and tips](payment-choices.md) before implementing them. For an unspecified mode, clarify the device arrangement; for USB, determine the supported protocol. Missing choices still allow independent preparation. A missing credential still allows compilation.

## Inspect before changing

Perform these checks even when the user supplies only a mode. Start with the [local build environment](build-environment.md), including the IDE's selected Gradle JDK. Read configuration structure and field names without printing secret values.

| Check | Reuse when present | Add or repair when missing |
|---|---|---|
| Local Gradle environment | Check wrapper/AGP compatibility, terminal and IDE Gradle JVMs, and daemon overrides | Select a compatible installed JDK in project-local settings; verify terminal build and IDE sync separately |
| SDK dependency in the target module | Trace direct coordinates, catalog aliases/bundles, convention plugins and shared modules; retain the selected version | Consume an existing alias first; otherwise add the SDK through the project's dependency convention |
| Dependency resolution | Inspect the selected variant's runtime graph; verify any local AAR's version and runtime libraries | Add only missing repositories/runtime libraries; [quick start](quick-start.md#1-resolve-dependencies) lists baseline requirements |
| Android configuration | Keep compatible minSdk/toolchain, merged manifest and consumer rules | Address actual compatibility gaps and register the app's Application when needed |
| Credentials and initialization | Reuse local provisioning and the existing SDK owner | Add local configuration fields and initialize once after credentials are available; empty keys keep runtime actions disabled |
| Connection and call sites | Reuse the payment owner, callbacks, status UI and cleanup | Wire the requested factory into the existing connection action and owner lifecycle; keep UI updates on the main thread |
| Existing checkout and requested functions | Preserve order amounts, storage, unresolved attempts and explicit function/tip choices | Ask about unspecified SALE/AUTH/tip scope, then use the payment sections below for confirmed monetary operations |

A catalog entry alone does not put the SDK on the app's classpath. Trace whether the target module consumes it, then verify resolution. Conversely, an SDK brought in through a convention plugin or shared module can already be present without a direct coordinate in the app build file. Avoid adding a second Maven declaration or mixing an existing AAR with the same Maven artifact.

Use the actual module/variant, for example:

```bash
./gradlew :checkout:dependencyInsight --dependency sunbay-taplink-sdk-android --configuration debugRuntimeClasspath
```

For local AARs or runtime-library checks, also inspect `:checkout:dependencies --configuration debugRuntimeClasspath`. Resolve custom catalogs/plugins only as needed. If Gradle resolution is unavailable, report the configuration evidence and exact failure separately; a failed lookup does not prove the SDK is absent. Continue edits supported by the files and leave runtime resolution explicitly unverified.

For APPtoAPP, reuse an explicit `AppToAppMode` choice; otherwise use the verified factory default (`CUSTOM`). No endpoint or USB selection is needed. A mode change preserves pending transaction records and recovery against the original terminal. Connect the existing UI/owner path; if the project lacks that host, identify the exact missing entry point and finish independent setup first.

## Make the app wiring concrete

Apply this section when integrating payments or repairing existing checkout behavior.

The samples expose a journal interface to reuse the app's storage. It is a seam to implement in the actual project, not the final integration by itself.

| Seam | Required behavior in app code |
|---|---|
| `reserve(request)` | Atomic durable record of UUID, order, operation, integer amount/currency and selected terminal/merchant/endpoint; reject duplicate or unresolved checkout before send |
| `recordResult(id, result)` | Associate with captured original ID; apply confirmed final outcomes idempotently; retain processing/unknown |
| `recordError(id, error)` | Retain pending attempt and sanitized error; keep recovery possible when callback IDs are missing |
| Startup/recreation | Read unresolved attempts and saved terminal identity from existing storage; keep checkout gated until reconciled |
| Connection identity | Read/compare metadata automatically for same-device or already selected targets; ask only to resolve an ambiguous target; persist identity with the attempt |
| Checkout action | Validate order/amount; generate new UUID; serialize submission through the payment owner |
| Result UI | Main-thread approved/declined/pending presentation, explicit original-ID query action, and storage-error state |
| Lifecycle | Payment owner disconnects/removes listener; cancel polling/UI work, preserve durable attempts and pending writes |

If the app has no durable attempt store, implement a small store using its established persistence approach. Do not present an in-memory sample as completed recovery. If a required store operation is unavailable, identify that exact seam and keep affected runtime actions disabled.

With an existing `OrderRepository`/`PaymentService`, map the sample journal methods to that interface; avoid a second database or a parallel payment manager. Journal failures need concrete app error handling: stop submission on reservation failure, retain unknown outcomes on result-write failure, show a storage-error state and require repair/reconciliation before another payment. Kotlin journal exceptions must reach this owner-level handler; the Java asset emits `STORAGE_ERROR` and blocks new SALE on that instance.

## Connection to first payment

1. Initialize with locally provisioned credentials and the selected config.
2. Connect. Clear payment readiness on disconnect/reconnecting.
3. Call `TaplinkSDK.getClient().getTerminalInfo(callback)` and compare actual SN/merchant with provisioning or a saved attempt. In APPtoAPP, automatically save the initial verified identity; the same-device target is already selected. Use a connection generation/owner guard so a late response from an older connection cannot mark a new connection ready. For ambiguous cross-device targets, follow [identity by mode](connections.md#terminal-identity-by-mode).
4. Submit SALE only on an explicit checkout action after identity readiness; transaction permission is checked separately by Tapro.
5. Keep PROCESSING/unknown/errors pending; offer query with the saved original ID. Reconnect to the saved terminal for recovery, verify identity again and bound polling.

Read the [Kotlin metadata callback](../taplink-protocol-reference.md#read-terminal-metadata) or [Java metadata callback](java-and-groovy.md#terminal-identity) for the exact API. Follow [recovery](recovery.md) for 306/307/308 and unresolved results.

## Verify the checkout entry path

Trace the implemented path from the user's action through the real application owner to `TaplinkSDK.getClient().sale(...)` (or the selected operation). A standalone adapter or an APK containing a Pay label leaves this check incomplete.

- For a new APPtoAPP SALE app, the ordinary path is **Connect → enter order/amount/currency → Pay → complete payment/tip on Tapro**. Run metadata binding automatically. Existing apps retain their established checkout flow.
- Verify the actual screen/caller owns the payment controller, the click handler invokes it, and normal configured/connected state enables the action. List the conditions that can still block it, using current project evidence.
- Every blocking condition has a visible explanation and next action near Pay: missing configuration, connecting/metadata read, identity mismatch, storage error, invalid input or an unresolved payment. Preserve actual Host error codes/messages; an unsupported metadata action must not leave an unexplained disabled button.
- Test readiness and submission through the application's payment owner with a fake terminal boundary, including successful same-device metadata without an extra confirmation, initial binding failure, stale callbacks, duplicate taps and original-ID recovery. Check UI/caller wiring separately from model tests.
- When the user reports "cannot start payment," first establish whether Pay is disabled, a click reaches the SDK, or Tapro returns an error. Inspect the blocking state and call path before adding configuration switches or another dialog. If runtime evidence is still needed, request that exact status/error; keep credentials and full payment payloads private.
- Local checks can establish wiring and state transitions. Record actual terminal/payment execution separately. If the user will perform device validation, provide the updated artifact and minimal manual path; respect that choice.

## Build and handoff

Use the existing wrapper, compatible local Gradle JVM and actual module/variant. Verify the [terminal and IDE environment](build-environment.md#verify-the-selected-runtime-and-ide-import) separately. Typical unflavored tasks:

| Project | Compile | Runtime dependency inspection |
|---|---|---|
| Kotlin app | `./gradlew :app:compileDebugKotlin` | `./gradlew :app:dependencies --configuration debugRuntimeClasspath` |
| Java app | `./gradlew :app:compileDebugJavaWithJavac` | Same configuration, adapted to the actual variant |

For mixed/flavored apps, use existing task names; `:app:tasks --all` can resolve them. Reuse the current SDK/toolchain. Missing credentials are unrelated to local compilation. A build failure should include the command and relevant error without secrets; avoid labeling setup complete until the required check actually passes.

The final handoff identifies what is code/build-ready and the next device action: configure local credentials, verify the selected terminal, then exercise the first payment and original-transaction query. Preserve all pending records during that handoff.

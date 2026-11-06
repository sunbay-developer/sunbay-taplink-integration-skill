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

Ask only when a choice remains unresolved after inspection. An APPtoAPP request already specifies the same-device route. For an unspecified mode, clarify the device arrangement; for USB, determine the supported protocol. One missing mode/endpoint still allows independent preparation. A missing credential still allows compilation.

## Inspect before changing

Perform these checks even when the user supplies only a mode. Read configuration structure and field names without printing secret values.

| Check | Reuse when present | Add or repair when missing |
|---|---|---|
| SDK dependency in the target module | Trace direct coordinates, catalog aliases/bundles, convention plugins and shared modules; retain the selected version | Consume an existing alias first; otherwise add the SDK through the project's dependency convention |
| Dependency resolution | Inspect the selected variant's runtime graph; verify any local AAR's version and runtime libraries | Add only missing repositories/runtime libraries; [quick start](quick-start.md#1-resolve-dependencies) lists baseline requirements |
| Android configuration | Keep compatible minSdk/toolchain, merged manifest and consumer rules | Address actual compatibility gaps and register the app's Application when needed |
| Credentials and initialization | Reuse local provisioning and the existing SDK owner | Add local configuration fields and initialize once after credentials are available; empty keys keep runtime actions disabled |
| Connection and call sites | Reuse the payment owner, callbacks, status UI and cleanup | Wire the requested factory into the existing connection action and owner lifecycle; keep UI updates on the main thread |
| Existing checkout | Preserve order amounts, storage and unresolved attempts | Repair affected wiring; use the payment sections below when the task includes monetary operations |

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
| Connection identity | Read terminal info after connection; enroll/compare SN and merchant, then persist accepted identity with the attempt |
| Checkout action | Validate order/amount; generate new UUID; serialize submission through the payment owner |
| Result UI | Main-thread approved/declined/pending presentation, explicit original-ID query action, and storage-error state |
| Lifecycle | Payment owner disconnects/removes listener; cancel polling/UI work, preserve durable attempts and pending writes |

If the app has no durable attempt store, implement a small store using its established persistence approach. Do not present an in-memory sample as completed recovery. If a required store operation is unavailable, identify that exact seam and keep affected runtime actions disabled.

With an existing `OrderRepository`/`PaymentService`, map the sample journal methods to that interface; avoid a second database or a parallel payment manager. Journal failures need concrete app error handling: stop submission on reservation failure, retain unknown outcomes on result-write failure, show a storage-error state and require repair/reconciliation before another payment. Kotlin journal exceptions must reach this owner-level handler; the Java asset emits `STORAGE_ERROR` and blocks new SALE on that instance.

## Connection to first payment

1. Initialize with locally provisioned credentials and the selected config.
2. Connect. Clear payment readiness on disconnect/reconnecting.
3. Call `TaplinkSDK.getClient().getTerminalInfo(callback)` and compare actual SN/merchant with the intended enrollment or saved attempt. Use a connection generation/owner guard so a late response from an older connection cannot mark a new connection ready.
4. Submit SALE only on an explicit checkout action after identity readiness; transaction permission is checked separately by Tapro.
5. Keep PROCESSING/unknown/errors pending; offer query with the saved original ID. Reconnect to the saved terminal for recovery, verify identity again and bound polling.

Read the [Kotlin metadata callback](../taplink-protocol-reference.md#read-terminal-metadata) or [Java metadata callback](java-and-groovy.md#terminal-identity) for the exact API. Follow [recovery](recovery.md) for 306/307/308 and unresolved results.

## Build and handoff

Use the existing wrapper and actual module/variant. Typical unflavored tasks:

| Project | Compile | Runtime dependency inspection |
|---|---|---|
| Kotlin app | `./gradlew :app:compileDebugKotlin` | `./gradlew :app:dependencies --configuration debugRuntimeClasspath` |
| Java app | `./gradlew :app:compileDebugJavaWithJavac` | Same configuration, adapted to the actual variant |

For mixed/flavored apps, use existing task names; `:app:tasks --all` can resolve them. Reuse the current SDK/toolchain. Missing credentials are unrelated to local compilation. A build failure should include the command and relevant error without secrets; avoid labeling setup complete until the required check actually passes.

The final handoff identifies what is code/build-ready and the next device action: configure local credentials, verify the selected terminal, then exercise the first payment and original-transaction query. Preserve all pending records during that handoff.

---
name: taplink-integration
description: Use when integrating or troubleshooting SUNBAY TapLink Android SDK payments with Tapro in Kotlin or Java POS apps, including APPtoAPP, App-to-App, APP_TO_APP, On-Device, LAN, USB or Sub-Screen setup, first SALE, refund/auth/query and 306/308 recovery.
license: MIT
---

# TapLink Android SDK Integration

Deliver working changes in the consumer's Android project. Packaged baseline: **SDK 1.1.0 + Tapro release_1.0.5**, checked **2026-11-04**. Keep an existing chosen version; use its artifact/release notes for differences. New integrations without a selected version use this verified baseline.

Accept a short request such as **"Help me integrate APPtoAPP mode."** as an actionable integration task. Inspect the project first; obtain dependency, language, configuration and lifecycle details from files. APPtoAPP / App-to-App / APP_TO_APP / On-Device all select the same-device route; use `ConnectionConfig.createOnDeviceMode()` for SDK 1.1.0. The mode is selected; ask about unspecified payment functions and tips as described below.

## Choose the shortest route

| User task | Start here | Deliver |
|---|---|---|
| Integrate a mode / change connection mode | [Project inspection](references/project-wiring.md#inspect-before-changing), [payment choices](references/payment-choices.md), then the selected [connection](references/connections.md) | Environment and connection wiring, followed by the confirmed payment functions; connection-only completion requires that explicit scope |
| Existing POS project adds TapLink | [Existing POS structure](references/existing-pos.md), then [project wiring](references/project-wiring.md) | Placement in the current modules, payment abstractions, order store and checkout path |
| First integration / first SALE | [Project wiring](references/project-wiring.md), then [quick start](references/quick-start.md) | Dependency, initialization, one selected connection, durable SALE/query flow, UI and cleanup wired into the app |
| Existing integration needs one operation | [Protocol recipes](taplink-protocol-reference.md) and the existing payment owner | The requested operation using the same storage, callbacks and lifecycle |
| Connection problem / 303 / 306 / 307 / 308 | [Recovery](references/recovery.md); [connections](references/connections.md) only for transport issues | Targeted diagnosis/fix with original-transaction recovery preserved |
| Java source or Groovy Gradle | [Java/Groovy guide](references/java-and-groovy.md) | Native project language/DSL; adapt the [Java asset](assets/TaplinkQuickStartJava.java) when an adapter is needed |
| Onboarding, Sandbox, go-live, HTTP services | [Documentation lookup](references/documentation-lookup.md) | Current official steps/schema evidence; continue independent Android work |
| Version mismatch / supplied SDK llms.txt | [Source baseline](references/source-baseline.md) | Relevant version corrections using the target artifact |

Read only the selected route. A troubleshooting request does not need a new adapter or a first-integration walkthrough. Private SDK/Host checkout access is optional; packaged examples work without it.

## Confirm payment scope

For a new integration with only a mode/connection request, ask early which functions are needed: **SALE, AUTH, both, or connection only**, and whether **tips** are needed. Follow [payment choices](references/payment-choices.md) for concise questions and conditional follow-ups. Reuse explicit answers and existing requirements; an explicit connection-only request needs no payment questionnaire. While awaiting answers, continue environment checks and connection preparation. Keep payment choices pending until answered; a working connection alone leaves an unspecified payment scope open.

## Work in the consumer's project

1. Check the [local build environment](references/build-environment.md) before changing dependencies: project wrapper/AGP, terminal Gradle JVM, IDE Gradle JVM and any daemon JVM criteria. Resolve hidden/local IDE settings explicitly, including first-import defaults. Run the [project inspection](references/project-wiring.md#inspect-before-changing). If checkout/order/payment code already exists, first [review its actual structure](references/existing-pos.md) and map each integration responsibility to concrete files/symbols. Summarize placement and gaps, then proceed; obtain facts available in code without asking the user to repeat them.
2. Resolve the mode from the request and existing setup, then confirm unspecified payment functions/tips. Ask only unresolved implementation choices, such as an ambiguous target module or USB protocol. APPtoAPP needs no LAN address or USB protocol. Continue independent work while awaiting a necessary choice. Real credentials stay local; their absence still permits compilation.
3. Implement the confirmed scope at those integration points. Reuse existing payment interfaces, DI/factories, durable order storage, checkout UI and connection ownership; preserve versions and credential provisioning. Add only missing responsibilities. Adapt the matching request/callback recipe: [Kotlin](assets/TaplinkQuickStart.kt) or [Java](assets/TaplinkQuickStartJava.java), including the durable store bridge, UI, lifecycle and terminal identity verification. Use real order inputs or caller-supplied amounts when wiring checkout. APPtoAPP already selects Tapro on the same device: verify/bind metadata automatically and expose Pay directly when ready. Ask for terminal selection only when the target is ambiguous; follow [mode-specific identity handling](references/connections.md#terminal-identity-by-mode).
4. Verify the [checkout entry path](references/project-wiring.md#verify-the-checkout-entry-path), then compile the actual module/variant using its wrapper and a verified compatible Gradle JVM. Inspect runtime dependencies and address compiler errors against the target artifact. For Android Studio users, also verify the project's Gradle JDK and sync when accessible. Report terminal build and IDE sync separately; missing toolchains, dependencies or IDE access leave the corresponding check unverified.

## Payment invariants

- Public entry: `com.sunmi.tapro.taplink.sdk.TaplinkSDK`; transactions through `getClient()`. Java uses the verified `*SDK` static methods in its guide. Keep implementation/communication packages out of consumer imports.
- Use On-Device, explicit Cross-Device LAN/Cable, or Sub-Screen. In 1.1.0 the Cable/transport enums have no AUTO or SDK Cloud strategy.
- Integer smallest currency units: `AmountInfo.of(1000L, "USD")` is $10.00. Keep tip separate from `orderAmount`; choose `tipAmount` or `tipConfig`.
- Persist a new UUID, order and verified terminal association **before** each new monetary operation. Atomically block duplicate/unresolved checkout and serialize submissions. Query uses the original ID, including after app restart.
- `onSuccess` branches on `isSuccess()` / `isProcessing()` / `isFailed()` plus unknown fallback. Approval requires code `100` and status `SUCCESS`. Technical errors use `onFailure`. Capture original IDs because response/error IDs may be null.
- For 306/308, disconnect or lost response, query the original operation before another payment decision. For 307, inspect rejection/busy details and resolve earlier unknown outcomes. A later authorized attempt after 306/307/308 uses a fresh ID. Reconnection never replays SALE.
- Global timeout is **seconds** (180 default); request timeout is **milliseconds**. On-Device/App-to-App has no callback timeout timer; a local UI deadline leaves payment unresolved.
- UI callbacks switch to main thread. One owner cleans up with disconnect + listener removal; pending records survive. Storage failures keep checkout blocked until repaired/reconciled.
- Inject credentials locally and disable production SDK logs. Avoid logging full payment/card payloads. Use the deployment's approved provisioning/storage; BuildConfig values remain extractable from the APK.

## Finish with evidence

Return: **changed files and integration placement; confirmed payment/tip scope and pending choices; shortest user path to Pay; module/language/mode/versions; wrapper/AGP and terminal/IDE Gradle JVMs; local configuration location; exact build command/result and IDE sync status; next device check**. Distinguish code/build readiness, verified terminal connection and verified payment. Each disabled payment action shows its concrete reason and next action nearby. Exercise the actual checkout entry path plus approved/declined, processing/unknown, timeout/disconnect, duplicate taps and lifecycle recovery through the project's authorized validation process. If device validation is delegated to the user, deliver the local checks and exact manual steps without operating a device. A connected socket or terminal metadata alone does not establish permission to perform SALE.

---
name: integrating-taplink-sdk
description: Use when integrating or troubleshooting TapLink Android SDK payments with Tapro, including On-Device, LAN, USB Cable, Sub-Screen, sale, refund, auth, transaction query, and timeout recovery.
---

# Integrating TapLink SDK

Help an integrator reach a working first payment in their existing Android app, then add requested operations. Target: **SDK 1.1.0 + Tapro release_1.0.5**, checked **2026-11-04**. Exact revisions and evidence: [source baseline](references/source-baseline.md).

## Start with the shortest useful path

1. Inspect Gradle, manifest, payment/order storage, lifecycle ownership and UI conventions. Infer existing choices before asking. Establish SDK/Tapro versions, deployment mode and requested operations.
2. Read [quick start](references/quick-start.md) and adapt [the Kotlin example](assets/TaplinkQuickStart.kt). Start with initialization, one connection, SALE, result handling, original-transaction query and cleanup. Extend requested flows; one adapter can serve the selected connection modes.
3. Confirm **where credentials will be injected**, without asking for secrets in chat. Missing credentials allow code generation and compilation. Authenticated device testing requires provisioned app credentials and terminal. `merchantId` is optional for local initialization.
4. Compile the consumer app, inspect runtime dependencies, then report verification and outstanding device checks. Follow its existing specification, test and approval requirements.

Use these pinned references for 1.1.0; private SDK/Tapro repository access is optional for integrators. For another version, check its artifact/source and release notes. If current web examples conflict with the target artifact, retain the target version's signatures and report the discrepancy.

## Read only what the task needs

| Task | Reference |
|---|---|
| First payment, Gradle, credentials, imports, lifecycle | [Quick start](references/quick-start.md), [Kotlin example](assets/TaplinkQuickStart.kt) |
| On-Device, LAN, Cable, Sub-Screen, discovery, USB | [Connections](references/connections.md) |
| Refund, void, auth/post-auth, abort, tips, batch, terminal info | [Protocol and operations](taplink-protocol-reference.md) |
| 303/306/307/308, lost response, query/reconnect | [Recovery](references/recovery.md) |
| Version compatibility or skill maintenance | [Source baseline](references/source-baseline.md) |

## Essential integration rules

- Public entry: `com.sunmi.tapro.taplink.sdk.TaplinkSDK`; transactions: `TaplinkSDK.getClient()`. Keep implementation packages (`impl`, `manager`, `protocol`, `persistence`, `util`, `communication`) out of consumer code.
- Select On-Device, Cross-Device LAN, explicit Cable protocol, or Sub-Screen. SDK 1.1.0 has no `AUTO` cable/transport selection or SDK Cloud connection.
- Use integer **smallest currency units**: `AmountInfo.of(1000L, "USD")` is $10.00. Keep tip out of `orderAmount`; choose either `amount.tipAmount` or `tipConfig`.
- Generate a UUID per new monetary operation; persist it with order and terminal association **before sending**. Query uses the original ID. Serialize submissions and block duplicate taps/unresolved payments in the app's payment owner.
- In `onSuccess`, check `isSuccess()`, `isProcessing()`, `isFailed()` and an unknown-state fallback. `isSuccess()` requires code `100` and status `SUCCESS`. `onFailure` handles technical errors. Capture the original ID in the callback closure; response/error IDs may be absent.
- For `306`, `308`, disconnect or lost response, query the original transaction before another payment decision. For `307`, inspect rejection/busy details and resolve any earlier unknown payment. A later authorized retry after `306/307/308` uses a new ID. Reconnection never authorizes replaying SALE.
- Global `TaplinkConfig.timeout`: **seconds**, default 180. Per-request `requestTimeout`: **milliseconds**. On-Device/App-to-App ignores callback timeout; a UI deadline leaves the payment unresolved.
- UI updates run on the main thread. One owner manages the SDK singleton and calls `disconnect()` + `removeConnectionListener()` at lifecycle end. Keep pending records through recreation/process death.
- Inject credentials locally, disable production SDK logs, avoid logging full payment payloads/card secrets. BuildConfig embeds values in the APK; use the deployment's approved provisioning/storage approach.

## Deliver a usable integration

Return changed files, selected mode, pinned versions, local credential setup, build result and next device check. Exercise approved, declined, processing/unknown, timeout/disconnect, duplicate taps and cleanup. Report compilation separately from authenticated device validation. Connection success and terminal metadata alone do not establish permission to perform SALE.

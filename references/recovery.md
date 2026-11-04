# Recovery and troubleshooting

## Preserve transaction identity

Before sending, durably record the operation, order, amount/currency, request UUID and selected terminal. Store returned transaction ID and final result against that same attempt. Block duplicate checkout taps and parallel unresolved attempts for one order.

Every new monetary operation uses a new UUID. Query retrieves an existing operation using its **original** request ID or transaction ID. Abort references the ongoing request ID. Retain the original ID in callback closures rather than relying on nullable `result.transactionRequestId` or `error.transactionRequestId`.

## Bounded recovery

1. On `PROCESSING`, unknown result, `306`, `308`, lost response or disconnect after submission, keep the order pending. Preserve the original record; reconnect to the same terminal if necessary.
2. Query `QueryRequest.byTransactionRequestId(originalRequestId)` using `TaplinkSDK.getClient().query(request, callback)`.
   Java uses `new QueryRequest().setTransactionRequestId(originalRequestId)` with the same client call; keep the captured original ID when callback IDs are missing.
3. Inspect `isSuccess()`/`isFailed()`/`isProcessing()` plus an unknown fallback. Apply confirmed final outcomes idempotently. A failed query or missing result leaves reconciliation open.
4. Use one in-flight query per attempt. Bound polling by the consumer's approved deadline/attempt limit, with delay/backoff and cancellation at owner shutdown. An example app policy is three query attempts, five seconds apart; this is an application choice, not an SDK guarantee.
5. After the query budget ends, stop automatic polling, keep the original record and show “Pending confirmation” with an operator/support route. Resume reconciliation on explicit action or the app's established recovery schedule. Exhausting retries never establishes that payment failed.
6. Create a new monetary attempt only after the original outcome and the business decision permit it. After `306/307/308`, that attempt uses a fresh ID. Never loop over SALE as a recovery action.

A coroutine/UI timeout or cancellation only stops the local wait. It does not establish terminal cancellation. An acknowledged abort still requires resolving the original payment's final outcome before another charge. Gateway-authorized operations follow the applicable void/refund flow.

## Timeout units and version behavior

| Setting | Unit | SDK 1.1.0 behavior |
|---|---|---|
| `TaplinkConfig.timeout` | Seconds | Default 180 |
| Request `.setRequestTimeout(30_000L)` | Milliseconds | Positive per-request value overrides global timeout |
| Missing/non-positive request timeout | — | Falls back to global seconds converted to milliseconds |
| `ConnectionConfig.connectionTimeout` | Seconds | Transport connection timeout; separate from payment timeout |
| On-Device / App-to-App callback | — | Ignores callback timer; no timer-generated `306` |
| LAN/Cable callback | — | One `306` on timeout; query original outcome |

Tapro's 1.0.5 branch closes cancellable LAN/Cable flows on request timeout. A flow past its cancellation point may finish locally after the callback expires. Card-reading timeout is a terminal `FAILED` result with message `read card timeout`. Keep it distinct from technical `306`.

## Error triage

Preserve actual `code`, `message`, operation ID and trace ID for sanitized diagnostics. Codes may arrive through different response paths: first distinguish `PaymentResult` from `PaymentError`, then inspect the result status/details. `PaymentError.canRetryWithSameId` is only an ID hint, not a payment-replay policy.

| Code/symptom | Meaning/action |
|---|---|
| `201` / SDK uninitialized | Check local config availability and initialize once |
| `231` / `232` | Tapro missing or unreachable locally; verify package/install and deployment |
| `241` / `242` | LAN connect/discovery failure; inspect selected host/port, service and network isolation |
| `251`–`255` | USB connection/permission/timeout/protocol/serial readiness; inspect hardware and SDK-selected protocol |
| `301` / `302` | Missing fields or invalid format; validate builder input and amount units |
| `303` | Unsupported action or app/API permission rejection; retain Host message, verify mode/action permission, app credentials and terminal onboarding. Host also uses this for unsupported QR void. Resolve the reported cause before a new attempt |
| `304` | Send failure; inspect delivery certainty. If delivery/outcome is uncertain, query the original attempt |
| `305` | Transaction in progress; serialize operations and reconcile the active attempt |
| `306` | Response timeout; query first |
| `307` | Transaction rejected, including Host busy/initialization conditions. Inspect reason, wait for current activity, resolve earlier unknown outcomes. A permitted new attempt uses a fresh ID |
| `308` | Transaction processing; query original outcome first. A later permitted new attempt uses a fresh ID |
| `309` | Transaction terminated; inspect final context instead of automatic retry |
| `312` / query failure | Query did not resolve the payment; retain pending state and original ID |
| `351` | Sub-Screen opening failed after transport setup; verify supported terminal build/hardware |
| Terminal-info failure | Preserve actual Host code/message. 1.1.0 does not normalize unsupported responses to `352` |
| No callback after On-Device UI timeout | Reconcile original operation; App-to-App has no callback timer-generated 306 |
| `NoClassDefFoundError` | Inspect runtime graph: AndroidX/coroutines, QR dependencies and copied-AAR transitive dependencies |

A connected socket validates transport. INIT, authentication, app/API permissions and processor support are checked further along the flow. Keep user messages short while retaining sanitized technical diagnostics for support.

## Integration verification

Use these scenarios for an agent dry run and for deployment acceptance. Device/backend actions follow the user's authorization and test environment.

| Scenario | Expected integration behavior |
|---|---|
| Pure Java / Groovy consumer | Uses the Java asset and native DSL; compiles against 1.1.0 without adding a Kotlin source plugin |
| Existing payment owner; one failing callback | Applies a targeted fix; preserves the existing language, store, selected mode and working initialization |
| Offline skill ZIP | Installs a complete `taplink-integration/` folder; references/assets resolve without private source access |
| No credentials yet | Produces code/config wiring and compiles; runtime connection stays disabled until locally provisioned |
| Chinese Sandbox preparation request | Uses Chinese public docs where available; distinguishes documentation environment from payment environment |
| Documentation index returns 404 | Uses verified official page/navigation fallback and packaged version evidence; keeps independent integration work moving |
| Current docs show another SDK version | Retains the consumer's pinned version and checks its source/artifact signatures |
| User supplies SDK-local `llms.txt` declaring 1.0.9 | Reads the supplied file, reports version drift, uses 1.1.0 Maven dependency and explicit Cable protocol, checks success code 100/status SUCCESS, and queries the original transaction for 306/308 |
| SDK-local `llms.txt` is absent | Continues from packaged references; does not require a private checkout or infer website index availability |
| Merchant-wide transaction history | Locates the appropriate documented service and field/schema evidence before adding a backend call |
| $10 SALE | Sends integer 1000 cents; UUID persisted before send; full public imports resolve against 1.1.0 |
| Approved / declined | Only `isSuccess()` settles as approved; `isFailed()` records decline |
| PROCESSING / unknown status | Retains pending state and offers original-ID query |
| Error contains no request ID | Uses captured/persisted original ID without `!!` |
| 306 / 308 / lost response | Queries original operation; no automatic SALE replay |
| 307 with prior unknown payment | Investigates busy/rejection and resolves prior outcome; new ID only for an authorized new attempt |
| Query keeps failing | Stops at configured limit, preserves pending record and recovery route |
| App restart / rotation | Keeps original identity; correct owner cleans connection and listeners; no duplicate send |
| Multiple LAN terminals | User/merchant selects terminal; recovery remains bound to original terminal |
| Shared terminal across POS apps | App coordinates use; no invented SDK reservation/ownership API |
| Referenced refund / post-auth / abort | Uses exact versioned builders and correct original-reference fields |
| Release/minified app | Logging disabled; dependency/manifest/R8 smoke checks pass |

When reporting results, distinguish source review, AAR compilation, model request checks and actual device/payment validation. List unperformed checks explicitly.

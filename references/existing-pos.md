# Integrate within an existing POS architecture

Use when the project already has checkout, order handling or payment code. Review
the affected call path before adding SDK code. Scope the review to the requested
integration; preserve the project's architecture and conventions.

## Read the actual checkout path

1. Read repository instructions, module dependencies and the target variant. Start
   at the real checkout button/caller; follow its presenter, ViewModel, use case,
   service or equivalent classes actually used by this application.
2. Follow an existing payment provider from request construction through result
   handling. Find its interface, routing/registration, order-state mapping and
   recovery of unresolved payments after restart.
3. Read the order/attempt store's reservation and finalization methods, transaction
   boundaries and schema. Locate the authoritative amount, currency, order/request
   IDs, terminal association and duplicate-submission guard.
4. Identify startup/provisioning, DI/manual factories, connection ownership,
   callback dispatch and teardown. Determine whether the connection belongs to a
   screen, signed-in session, service or application process. Check manifest process
   assignments if the application uses multiple processes.
5. Read relevant tests and logging/configuration conventions. Use the existing
   seams for verification and diagnostics.

Read implementations and active bindings, not only class names. An unused
`PaymentService` or sample Activity is insufficient evidence of the active path.

## Record integration placement before edits

Give a compact placement map with **actual file paths and class/function names**:

| Responsibility | Code evidence | Placement decision |
|---|---|---|
| SDK dependency and request/result mapping | Provider implementation module and imports | Use the Android integration/data module that consumes the SDK; preserve pure JVM/domain model boundaries |
| Initialization and credentials | Provisioning/startup owner and DI/factory binding | Initialize once after configuration is available, in the correct process, through the existing owner |
| Connection and cleanup | Connection/session owner and its lifetime | Reuse that owner for connect/disconnect/listener removal; screens detach their own UI observers |
| SALE, query and provider selection | Checkout caller, payment interface/service and routing registry | Implement/register TapLink behind the current abstraction and make the existing selection path reach it |
| Reservation and persistence | Order/attempt store and database transaction | Adapt the journal contract to this store; add missing fields/operations and necessary migrations |
| UI state and errors | Presenter/ViewModel/state holder and current controls | Connect the requested operation and status to the existing checkout screen/caller |
| Verification | Test seams and consuming app/library variants | Exercise the existing checkout route and recovery; compile the affected modules and app variant |

State what is reused and the smallest addition needed. This map is an implementation
decision, not another approval ceremony. Proceed when the code and scope determine
placement. Ask one focused question only if inspection leaves materially different
choices, such as two active checkout apps or an unspecified add-versus-replace
provider decision.

## Adapt to the current project

- Keep its language, architecture, DI and threading model. Layer names above are
  examples, not a requirement to introduce MVVM, MVI, Clean Architecture, a new DI
  framework or extra modules into a flat application.
- Implement TapLink behind an existing payment interface/provider layer when one
  exists. Convert inputs/results there. Keep Android Context and SDK models out
  of layers that currently expose platform-neutral business contracts.
- Preserve other providers, routing and non-requested operations. Add or replace a
  provider according to scope. Registering an unreachable adapter leaves the
  integration incomplete. Unknown payments remain tied to their original provider,
  terminal and request ID; selecting TapLink never reroutes an earlier attempt.
- Reuse real orders/totals/currency/IDs and the durable attempt store. Add missing
  capabilities at that owner rather than creating a second order database or
  payment manager. Preserve atomic reserve-before-send and idempotent results.
- Cleanup follows the connection owner's lifetime. A shared service/session
  connection survives rotation; Activity destruction detaches its UI observers.
  Owner shutdown disconnects and removes the SDK listener while retaining pending
  records. Avoid two owners controlling the SDK singleton.
- Treat packaged assets as request/callback recipes. Extract needed behavior into
  current abstractions. Add a checkout screen, Application wiring or coordinator
  only when that responsibility is absent and the project's conventions support it.

If current code lacks a required safety property, identify the exact gap and make
the smallest supported fix at its existing owner. Preserve order completion,
receipts, accounting and unrelated payment behavior outside the requested scope.

## Verify through the existing POS

Trace **existing checkout action → current owner/provider selection → TapLink
adapter → public SDK call → existing result/store/UI path**. Test that selecting
TapLink reaches its adapter and retained providers keep their routing. Use current
test infrastructure for relevant duplicate submission, unknown/query recovery,
result mapping and lifecycle checks.

Report placement, changed files and actual checks. Distinguish a compiled adapter
from a connected checkout route and from a device-tested payment. Follow
[checkout entry verification](project-wiring.md#verify-the-checkout-entry-path)
and the user's device-validation preference.

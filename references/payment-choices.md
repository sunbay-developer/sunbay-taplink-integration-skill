# Confirm the payment functions after choosing a mode

A connection mode determines how the app reaches Tapro. Ask for the payment scope when a new integration request names only APPtoAPP, LAN, USB or Sub-Screen, including a request to connect using that mode.

## Ask early and keep the questions short

After a brief project inspection, use the client's clarification interface when available, or ask in normal conversation. Start with two questions and reuse any answers already supplied:

1. **Which functions should be integrated: SALE, AUTH, both, or connection only?** Accept other supported operations if the user names them.
2. **Are tips needed?** If yes, determine whether the POS supplies the tip amount or Tapro collects it on screen. Preserve an existing explicit terminal-policy choice.

If the user explicitly requests connection only and excludes payments, complete that scope without asking about payment features. If the request already specifies SALE with no tips, implement that selection without asking again. For an existing payment app, describe the discovered functions briefly and ask whether the unspecified scope should retain them or add others. A focused connection bug fix retains the current payment behavior and needs no new feature questionnaire.

Continue JDK/Gradle checks, missing dependency setup, local credential injection and connection wiring while answers are pending. Implement payment calls and optional features after the relevant decisions arrive. If the user has not answered, report the completed connection work and the pending choices; do not label the whole integration complete or assume a default SALE amount.

## Follow up only on the selected functions

| Selection | Clarify when unresolved | Implementation route |
|---|---|---|
| SALE | Tip requirement and source; actual order amount/currency inputs | [Quick start](quick-start.md), existing checkout and durable SALE/query recovery |
| AUTH | Whether the workflow also needs completion (`POST_AUTH`), incremental authorization or void; requested tip behavior | [Transaction reference](../taplink-protocol-reference.md), preserving authorization references and original-ID recovery |
| Tips | No tips, POS-provided tip, terminal on-screen collection, or an explicitly chosen terminal policy | [Tip settings](../taplink-protocol-reference.md#tips-signature-and-receipts); verify the selected operation's request type and processor support |
| Additional functions | Refund, void, receipts, signature or other options the user requests or the selected workflow requires | Relevant operation/configuration in the transaction reference; add only the confirmed options |
| Connection only | No further payment choices | Connection state, local credentials, callbacks, lifecycle cleanup and build/IDE verification |

Keep the first exchange focused on functions and tips; ask about receipts, signature or authorization completion only when needed for the selected flow. Explain unfamiliar operation names in the user's language. Published prompt examples remain in English.

Include durable identifiers, result handling and original-transaction query/recovery with monetary operations. These are integration correctness requirements, not optional product features to switch off. Use the app's order inputs; keep credentials local.

Verify optional behavior against the packaged SDK/Host and processor evidence. In the pinned Tapro 1.0.5 baseline, `allowTipAdjustment` has no complete request path. A request for post-transaction tip adjustment therefore requires a supported capability check; do not promise enablement by adding a flag. For AUTH, inspect its exact request model before applying tip settings from a SALE example.

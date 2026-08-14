# integrating-taplink-sdk

An [Agent Skill](https://agentskills.io/specification) that guides an AI coding agent through integrating the **TapLink SDK** (SUNBAY/Sunmi payment terminal SDK) into an Android POS application — supporting three connection modes (App-to-App Intent, LAN network, Cable USB/serial) and all transaction types (sale, refund, void, auth, post-auth, query, batch close).

It is **architecture-aware**: instead of dumping boilerplate, it makes the agent:

1. **Verify the SDK version and protocol** against the official source (so the snapshot never stalls out-of-date advice)
2. **Confirm real credentials** (AppId, MerchantId, SecretKey) before writing code
3. **Pick the right connection topology** (same-device Intent / LAN network / USB cable)
4. **Inspect the host codebase** (MVI/MVVM, Hilt/Koin, existing device integration patterns) and **adapt to its conventions**
5. **Implement all transaction flows correctly** — with proper error handling (especially error 306 timeout recovery), idempotency via `transactionRequestId`, amount conversion (cents, not dollars), and thread-safe callbacks

## What's inside

| File | Purpose |
|---|---|
| `SKILL.md` | The complete playbook — 5 steps from credential gate to go-live checklist, topology decision flow, architecture-adaptation guide, transaction/error handling rules, common mistakes and red flags. |
| `taplink-protocol-reference.md` | SDK protocol snapshot — request/response shapes, error codes, thread safety, recovery patterns, amount handling, tips, PCI rules, and links to official sources. |
| `README.md` | This file — install instructions and overview. |

## Core principles

- **Do not invent the wire contract.** Reference `taplink-protocol-reference.md` + official SDK source.
- **No placeholder credentials.** Real AppId/MerchantId/SecretKey are required; compilation without them is a false success.
- **Amount precision.** All amounts in cents (smallest currency unit), never dollars or floats. `BigDecimal("1000")` for $10.00, not `10` or `10.0`.
- **`onSuccess` ≠ approved.** Check `PaymentResult.isSuccess()` / `isProcessing()` / `isFailed()`, never just the method name.
- **Error 306 is special.** Timeout means status unknown — **must query before retrying**, never auto-retry.
- **Thread safety required.** Callbacks run on background threads; wrap all UI updates in `runOnUiThread { }`.
- **Cleanup is non-negotiable.** `disconnect()` + `removeConnectionListener()` in `onDestroy()`, always both.

## Install

### GitHub Copilot CLI
Clone into your Copilot skills directory:

```bash
# macOS / Linux
git clone https://github.com/sunbay/taplink-sdk-skill ~/.copilot/skills/integrating-taplink-sdk

# Windows (PowerShell)
git clone https://github.com/sunbay/taplink-sdk-skill $env:USERPROFILE\.copilot\skills\integrating-taplink-sdk
```

Or per-project: clone into `<your-taplink-project>/.copilot/skills/integrating-taplink-sdk`.

Then invoke with:
```bash
copilot /integrating-taplink-sdk "Integrate TapLink SDK sale flow into my POS app"
```

### Claude CLI / Desktop
```bash
git clone https://github.com/sunbay/taplink-sdk-skill ~/.claude/skills/integrating-taplink-sdk
```

Invoke with `/integrating-taplink-sdk` or let Claude auto-discover it when you describe TapLink integration tasks.

### Other agents (Cursor, VS Code, custom LLM tools)
The content is plain Markdown — include `SKILL.md` + `taplink-protocol-reference.md` as:
- A rules file or system-prompt context
- A knowledge document pasted into the conversation
- A local reference the agent can read via file tools

The playbook and protocol snapshot transfer unchanged; only `invoke /skill-name` syntax is agent-specific.

### Update later

```bash
git -C ~/.copilot/skills/integrating-taplink-sdk pull
```

or for Claude:

```bash
git -C ~/.claude/skills/integrating-taplink-sdk pull
```

## How to use this skill

### Scenario 1: Starting fresh

You have a new Android POS app and want to add TapLink payments:

```bash
copilot /integrating-taplink-sdk "I'm building a POS app from scratch. Help me integrate TapLink SDK for card payments."
```

The skill will:
1. Ask for your real credentials (AppId, MerchantId, SecretKey)
2. Ask where the POS app runs (same device as Tapro? networked? USB?)
3. Inspect your project structure
4. Guide you through the 5-step integration, adapting to your codebase patterns

### Scenario 2: Troubleshooting

Your integration is stuck or payments aren't working:

```bash
copilot /integrating-taplink-sdk "My sale transactions keep timing out (error 306). How do I handle this?"
```

The skill will point you to the query-based recovery pattern and timeout rules.

### Scenario 3: Architecture guidance

You need to understand where payment logic should live in your app:

```bash
copilot /integrating-taplink-sdk "I have an MVI/Redux app with Hilt. How should I structure the TapLink integration?"
```

The skill will read your codebase, understand your patterns, and give you architectural guidance.

## Keeping it current

TapLink SDK updates frequently. **SKILL.md Step 0** instructs the agent to verify the snapshot against:
- The official Android SDK guide: https://docs.sunbay.us/en/sdk/client-side/android
- The published SDK artifact `com.sunmi:sunbay-taplink-sdk-android` (classes under `com.sunmi.tapro.taplink.sdk.*`)
- The release notes (linked from the docs site) for breaking changes

So day-to-day usage stays correct even if this snapshot lags a few days or weeks behind SDK releases.

When you (or an agent) spot a drift:
1. Check the official docs / SDK artifact.
2. Update `taplink-protocol-reference.md` (and the snapshot date).
3. Commit and push: `git commit -m "docs: update protocol reference for SDK vX.Y.Z"`
4. Everyone benefits on their next `git pull`.

## Key references

- **Official Android SDK guide:** https://docs.sunbay.us/en/sdk/client-side/android
- **SDK artifact (Maven):** `com.sunmi:sunbay-taplink-sdk-android`
- **Release notes:** linked from the docs site

## Contributing

PRs welcome — especially:
- Updating `taplink-protocol-reference.md` when the SDK adds fields or error codes
- Adding new common pitfalls or "red flags" to `SKILL.md`
- Improving the 5-step playbook based on real integration feedback
- Clarifying connection mode setup for LAN or Cable modes

Keep the snapshot date current with any material change. Use Conventional Commits (English) — e.g., `docs: add error code 312 to reference`.

## License

MIT — see [`LICENSE`](LICENSE).

---

## Quick start (TL;DR)

1. **Install:** Clone this repo into your Copilot/Claude skills directory.
2. **Invoke:** `/integrating-taplink-sdk` + describe your POS app.
3. **Provide:** Real AppId/MerchantId/SecretKey when asked (non-negotiable).
4. **Pick:** Connection mode (App-to-App / LAN / Cable).
5. **Adapt:** The skill reads your code and gives codebase-specific guidance.
6. **Implement:** Following Step 3–5 of `SKILL.md` (init, connect, transactions, go-live).
7. **Verify:** Check the go-live checklist before production release.

## Support

For issues with the skill itself, open a PR/issue in this repo.

For TapLink SDK questions:
- Read the official Android SDK guide: https://docs.sunbay.us/en/sdk/client-side/android
- Inspect the published SDK artifact `com.sunmi:sunbay-taplink-sdk-android` in your IDE
- Review the release notes (linked from the docs site) for breaking changes


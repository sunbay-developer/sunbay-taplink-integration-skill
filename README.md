# integrating-taplink-sdk

An agent skill for integrating **TapLink Android SDK 1.1.0** with **Tapro 1.0.5**, starting with one SALE and a reliable query/recovery path in an existing Android app.

Grounded in SDK tag `v1.1.0`, its AAR, and Tapro branch `release_1.0.5`. See [exact revisions and verification limits](references/source-baseline.md). Skill name remains `integrating-taplink-sdk`.

## Install

Clone the complete repository into a skills directory recognized by your agent, using `integrating-taplink-sdk` as the installed folder name. Keep the references and assets beside `SKILL.md`.

For clients supporting project-level `.agents/skills`:

```bash
git clone https://github.com/sunbay/taplink-sdk-skill .agents/skills/integrating-taplink-sdk
```

For Claude Code:

```bash
git clone https://github.com/sunbay/taplink-sdk-skill ~/.claude/skills/integrating-taplink-sdk
```

For other clients, use their configured skill directory or point the assistant at this repository's `SKILL.md`.

## Use

> Use integrating-taplink-sdk. Add a $10 card SALE to this Android app using SDK 1.1.0 and Tapro 1.0.5 on the same device. Include original-transaction query and lifecycle cleanup. Credentials will be configured locally.

> Use integrating-taplink-sdk. Our tablet connects to Tapro over LAN. Add discovery and terminal selection; handle a 306 timeout without duplicating payment.

Prepare and compile code before obtaining credentials. Supply real credentials through local provisioning for authenticated device tests; keep secret keys out of chat and source control.

## Contents

| File | Purpose |
|---|---|
| [SKILL.md](SKILL.md) | Agent entry point and essential payment rules |
| [Quick start](references/quick-start.md) | Gradle, credentials, initialization and first-payment wiring |
| [Kotlin example](assets/TaplinkQuickStart.kt) | Public API sample: SALE, query, callbacks and cleanup |
| [Connections](references/connections.md) | Explicit LAN/Cable, discovery, USB and Sub-Screen |
| [Protocol reference](taplink-protocol-reference.md) | Request recipes, amounts and follow-up operations |
| [Recovery](references/recovery.md) | Unknown outcomes, timeout units, errors and checks |
| [Source baseline](references/source-baseline.md) | Versioned evidence and maintenance guidance |

## Maintain

Check changes against the target SDK artifact and Tapro branch. Update the baseline and affected references together. Compile the Kotlin asset against the target AAR; run the application scenarios in [recovery](references/recovery.md#integration-verification). Validate metadata and local links. Retain the skill name and protocol-reference path for installed users.

The consumer supplies durable order storage and UI. Validate terminal, processor and merchant capabilities on the intended deployment. Use English Conventional Commit messages. MIT: [LICENSE](LICENSE).

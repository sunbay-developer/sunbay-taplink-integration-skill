# sunbay-taplink-integration-skill

An agent skill for integrating **TapLink Android SDK 1.1.0** with **Tapro 1.0.5**, starting with one SALE and a reliable query/recovery path in an existing Android app.

Grounded in SDK tag `v1.1.0`, its AAR, and Tapro branch `release_1.0.5`. See [exact revisions and verification limits](references/source-baseline.md).

## Naming

| Item | Name |
|---|---|
| Repository / distribution | `sunbay-taplink-integration-skill` |
| Skill identifier and installed directory | `taplink-integration` |
| Display title | TapLink Android SDK Integration |

## Install

Clone the complete repository into a skills directory recognized by your agent, using `taplink-integration` as the installed folder name. Keep the references and assets beside `SKILL.md`.

Use the project's GitHub clone URL:

```bash
TAPLINK_SKILL_REPO="https://github.com/sunbay-developer/sunbay-taplink-integration-skill.git"
```

For clients supporting project-level `.agents/skills`:

```bash
git clone "$TAPLINK_SKILL_REPO" .agents/skills/taplink-integration
```

For Claude Code:

```bash
git clone "$TAPLINK_SKILL_REPO" ~/.claude/skills/taplink-integration
```

For other clients, use their configured skill directory or point the assistant at this repository's `SKILL.md`.

### Migrate an existing installation

The project is now `sunbay-taplink-integration-skill`. Earlier project names were `taplink-sdk-skill` and `taplink-integration-skill`; the previous skill identifier was `integrating-taplink-sdk`. Update the existing checkout to the revision containing this rename, then rename its installed skill directory to `taplink-integration`. Preserve local changes and keep one installed copy. Update prompts and any explicit skill-path references to the new name, then reload skills or start a new agent session.

For an existing clone, update `origin` to `https://github.com/sunbay-developer/sunbay-taplink-integration-skill.git` with `git remote set-url origin <clone-url>`. Use `sunbay-taplink-integration-skill` for the project checkout directory and `taplink-integration` for the installed skill directory.

## Use

> Use taplink-integration. Add a $10 card SALE to this Android app using SDK 1.1.0 and Tapro 1.0.5 on the same device. Include original-transaction query and lifecycle cleanup. Credentials will be configured locally.

> Use taplink-integration. Our tablet connects to Tapro over LAN. Add discovery and terminal selection; handle a 306 timeout without duplicating payment.

> Use taplink-integration. Keep Android SDK 1.1.0 and Tapro 1.0.5. Check the Chinese official docs for Sandbox onboarding and test preparation, then finish the local integration code.

Prepare and compile code before obtaining credentials. Supply real credentials through local provisioning for authenticated device tests; keep secret keys out of chat and source control.

If you supply an SDK-local `llms.txt`, the skill reads its declared version and relevant examples, then reconciles them with the target SDK. The SDK-local file now targets 1.1.0; [migration corrections](references/source-baseline.md#sdk-local-llmstxt) explain differences in older 1.0.9 copies. The packaged references also work without an SDK checkout.

For current onboarding/process questions, the skill selects documentation by the user's language and uses clean Markdown/API schemas where the official site exposes them. The pinned SDK/AAR reference keeps code version-specific, and documented fallbacks handle unavailable indexes. See [documentation lookup](references/documentation-lookup.md).

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
| [Documentation lookup](references/documentation-lookup.md) | Supplied local llms.txt, version reconciliation, language/environment selection, official content and schema discovery, missing-index fallback |

## Maintain

Check changes against the target SDK artifact and Tapro branch. Update the baseline and affected references together. Compile the Kotlin asset against the target AAR; run the application scenarios in [recovery](references/recovery.md#integration-verification). Validate metadata and local links. Keep the skill identifier `taplink-integration` and protocol-reference path stable. Document future renames with installation migration steps.

The consumer supplies durable order storage and UI. Validate terminal, processor and merchant capabilities on the intended deployment. Use English Conventional Commit messages. MIT: [LICENSE](LICENSE).

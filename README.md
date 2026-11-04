# sunbay-taplink-integration-skill

Add **SUNBAY TapLink Android SDK 1.1.0 + Tapro 1.0.5** to an existing Kotlin or Java POS app: first SALE, original-transaction query, durable recovery and lifecycle cleanup. The agent adapts the integration to your current project.

## Get started

### 1. Install

Run in your Android project with [Node.js 22.20+ and the skills CLI](https://github.com/vercel-labs/skills):

```bash
npx skills add sunbay-developer/sunbay-taplink-integration-skill --skill taplink-integration
```

Select your coding agent when prompted. To target one explicitly, append `--agent codex` or `--agent claude-code`. Installation is project-local by default. Open a new agent session if the skill is not yet discovered.

**Without Node.js**, clone into the skill directory recognized by your client:

```bash
git clone https://github.com/sunbay-developer/sunbay-taplink-integration-skill.git .agents/skills/taplink-integration
```

For Claude Code's dedicated directory, use `.claude/skills/taplink-integration` as the destination. For an offline handoff, extract the supplied `taplink-integration.zip` into `.agents/skills/` (or your client's skill directory). The final path must be `.agents/skills/taplink-integration/SKILL.md`, with `assets/` and `references/` beside it. Keep the whole folder together.

### 2. Give the agent a task

Paste this in your **existing Android project**:

> Use taplink-integration. Add a $10 card SALE and original-transaction query using SDK 1.1.0 and Tapro 1.0.5. POS and Tapro run on the same device. Follow the project's existing language, Gradle files, order storage and UI. Credentials will be configured locally later. Implement the wiring and run the app's compile task.

For a LAN deployment:

> Use taplink-integration. Integrate SDK 1.1.0 and Tapro 1.0.5 into this Android POS over LAN. Start with a $10 card SALE and original-transaction query. Reuse the existing language, order storage and UI. Credentials will be configured locally later. Modify the project, compile it and list the next device checks.

The agent inspects the project first. Supply only choices it cannot infer: same device / LAN / USB / Sub-Screen, the intended terminal/protocol, and requested operations. Existing configuration and code supply the other details. Keep real app keys out of chat and source control.

### 3. Check the handoff

Expect changed project files, selected module/mode/versions, local configuration location, the exact build result and the next device test. You can compile before obtaining credentials. Provisioning, terminal identity, transaction permissions and test-payment results are verified on the intended deployment.

For an existing integration, keep the task narrow:

> Use taplink-integration. This Java app already takes LAN payments. Fix recovery for 306 and a missing callback request ID. Reuse our current payment service and order repository.

## What is included

| Resource | When you need it |
|---|---|
| [Skill entry point](SKILL.md) | Agent routing and essential payment invariants |
| [Project wiring](references/project-wiring.md) | Find the app module and implement storage, UI, terminal identity and lifecycle seams |
| [Quick start](references/quick-start.md) | Dependency, local credentials, initialization and first payment |
| [Java / Groovy guide](references/java-and-groovy.md) | Java signatures and Groovy Gradle setup |
| [Kotlin adapter](assets/TaplinkQuickStart.kt) / [Java adapter](assets/TaplinkQuickStartJava.java) | Compilable public-API samples to adapt to the existing app |
| [Transaction recipes](assets/TransactionRecipes.kt) / [operation reference](taplink-protocol-reference.md) | Refund, void, auth, tips, batch and terminal info |
| [Connections](references/connections.md) / [recovery](references/recovery.md) | Transport selection, discovery, unknown outcomes and errors |
| [Documentation lookup](references/documentation-lookup.md) | Official docs, local llms.txt, onboarding and schema discovery |
| [Source baseline](references/source-baseline.md) | Exact SDK/Host revisions, version corrections, workflow comparisons and verification limits |

The package works without private SDK/Host checkout access. For another selected SDK version, verify its artifact/API and release notes before adapting the examples.

## Names and migration

| Item | Name |
|---|---|
| Repository / distribution | `sunbay-taplink-integration-skill` |
| Skill identifier / installed directory | `taplink-integration` |
| Display title | TapLink Android SDK Integration |

Earlier project names were `taplink-sdk-skill` and `taplink-integration-skill`; the previous skill identifier was `integrating-taplink-sdk`. Update the existing checkout, rename its installed skill folder to `taplink-integration`, preserve local changes and keep one installed copy. Update explicit prompts/paths and reload the agent session.

For an older clone:

```bash
git remote set-url origin https://github.com/sunbay-developer/sunbay-taplink-integration-skill.git
```

## Package and maintain

From a source checkout, build a shareable ZIP with Python 3.9+ (standard library only):

```bash
python3 scripts/package_skill.py
```

Output: `dist/taplink-integration.zip`. The package contains the correctly named skill folder, references, examples, license and packaging helper. Git/IDE state, local properties and other files outside the distribution allowlist are excluded. The helper rejects resource symlinks and missing local link targets before replacing a previous delivery.

For maintenance, run `python3 -m unittest discover -s tests -v`, validate skill metadata/local links, compile language assets against the actual AAR and exercise relevant [integration scenarios](references/recovery.md#integration-verification). Keep target SDK/Host evidence and affected references aligned. Compilation, device/payment results and current publication status are separate evidence. Use English Conventional Commit messages. [MIT license](LICENSE).

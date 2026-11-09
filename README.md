# sunbay-taplink-integration-skill

A reusable integration skill for AI coding assistants that support Agent Skills. Add **SUNBAY TapLink Android SDK 1.1.0 + Tapro 1.0.5** to an existing Kotlin or Java POS app: first SALE, original-transaction query, durable recovery and lifecycle cleanup. The agent adapts the integration to your current project.

## Get started

### 1. Install

Run in your Android project with [Node.js 22.20+ and the skills CLI](https://github.com/vercel-labs/skills):

```bash
npx skills add sunbay-developer/sunbay-taplink-integration-skill --skill taplink-integration
```

Select your coding agent when prompted. Installation is project-local by default. Open a new agent session if the skill is not yet discovered.

**Without Node.js**, clone into the skill directory recognized by your client. This example uses `.agents/skills/`; adjust the destination to your client's directory:

```bash
git clone https://github.com/sunbay-developer/sunbay-taplink-integration-skill.git .agents/skills/taplink-integration
```

For an offline handoff, extract the supplied `taplink-integration.zip` into your client's skill directory. Keep the complete `taplink-integration/` folder, including `SKILL.md`, `assets/` and `references/`.

### 2. Ask in one sentence

Open your **existing Android project** and ask:

> Help me integrate APPtoAPP mode.

The agent handles the integration checklist:

- Detect the Android module, language, Gradle setup and existing SDK dependency.
- Check the local Gradle/JDK compatibility, including the IDE's Gradle JDK and sync status.
- Check repositories, SDK initialization, credential configuration, manifest and payment owner.
- Ask which payment functions and tip options you need, then integrate the confirmed choices.
- Reuse working code and add the missing APP-to-APP connection, UI and lifecycle wiring.
- Verify the payment button/caller reaches the SDK; explain any remaining block beside the action.
- Run the relevant build checks and report any remaining local or device setup.

APPtoAPP, App-to-App and On-Device refer to the same-device integration. For another deployment, name LAN, USB or Sub-Screen instead. The agent reads project files before asking for missing details; versions, dependencies and implementation steps can come from the project and skill. Keep real app keys in local configuration.

After selecting SALE and tips, a new APPtoAPP app should let you **connect, enter
the order amount/currency, and Pay**. Same-device terminal checks run automatically.
Terminal selection is requested only when the target is ambiguous, such as
multiple discovered LAN terminals. If Pay is unavailable, the app shows the reason
and the next action beside it.

If your client does not select the installed skill automatically, select **taplink-integration** in its skill picker and send the same request.

### 3. Check the handoff

Expect changed project files, confirmed payment/tip choices, selected module/mode/versions, local configuration location, build and IDE sync results, and the next device test. If payment choices are still pending, the agent reports them alongside connection progress. You can compile before obtaining credentials. Provisioning, terminal identity, transaction permissions and test-payment results are verified on the intended deployment.

## What is included

| Resource | When you need it |
|---|---|
| [Skill entry point](SKILL.md) | Agent routing and essential payment invariants |
| [Local build environment](references/build-environment.md) | Check wrapper, AGP, terminal/IDE Gradle JDKs and first import |
| [Payment choices](references/payment-choices.md) | Confirm SALE/AUTH, tips and relevant optional functions after a short mode request |
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

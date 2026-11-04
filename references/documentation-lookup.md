# Finding official documentation without losing the target version

Use this reference for onboarding, Sandbox preparation, go-live, new documentation, or questions beyond the packaged Android 1.1.0 reference. Use the local quick start and assets directly for the verified SDK path; load additional material when the task needs it.

## 1. Check supplied repository documentation

When the user provides an SDK checkout or a local `llms.txt`, inspect that file before additional web discovery. Record its declared SDK version and repository revision; read the sections relevant to the request. Its contents determine whether it is an integration reference, a link index, or both. Follow only links actually present in it.

The SDK-root `llms.txt` is an integration reference with code examples, updated locally to **1.1.0** on 2026-11-04. The pinned public `v1.1.0` tree predates this document update and does not contain that file. Inspect the supplied copy's own version: older copies declare **1.0.9** and need the [recorded migration corrections](source-baseline.md#sdk-local-llmstxt), including explicit USB protocol selection, result predicates and original-transaction recovery. The packaged quick start and compiled examples also cover the 1.1.0 integration path.

Access to this local file is optional. When it is absent, continue with packaged references. Repository `llms.txt` availability and website `/{language}/llms-md.txt` availability are checked independently; a local file supplies no evidence that a corresponding web URL exists. Onboarding and HTTP API questions still require the relevant current official pages or schemas below.

## 2. Select the language and documentation site

Use Chinese documentation for a Chinese conversation and English documentation for English. Default to the public documentation site, `https://docs.sunbay.us`.

| Entry | Chinese | English |
|---|---|---|
| Documentation index | `https://docs.sunbay.us/zh/llms-md.txt` | `https://docs.sunbay.us/en/llms-md.txt` |
| Developer documentation home | [Chinese docs](https://docs.sunbay.us/zh/docs) | [English docs](https://docs.sunbay.us/en/docs) |
| Android SDK page | [Chinese Android SDK guide](https://docs.sunbay.us/zh/sdk/client-side/android) | [English Android SDK guide](https://docs.sunbay.us/en/sdk/client-side/android) |

**Documentation environment and payment environment are separate choices.** A request for Sandbox payments still uses the public documentation site. Use `https://docs.sunbay-dev.com` only when the user explicitly requests development/test **documentation**. A user-provided mirror is another explicit choice. Switching documentation sites does not configure the terminal or credentials.

## 3. Read the relevant material

For web discovery, start with the selected language's index when available. Select entries for the actual question, such as Android integration, local payments, Sandbox setup, account/API enablement or a related service. Read only the relevant pages.

| Link offered by an entry | Use |
|---|---|
| `Markdown:` | Fetch the clean page body for procedures and explanations |
| `Schema:` | Fetch the HTTP API's request/response definitions, required fields and enums when implementing that endpoint |
| Public page / `Source:` | Cite this readable page in the user-facing answer |

Follow the exact links exposed by the index or page. API route names and Markdown/schema paths need evidence from the site. Kotlin imports, builders and callbacks still come from the target Android artifact/source; an HTTP schema does not define the Kotlin SDK surface.

If both language pages are available, use the user's language for explanations and links. If a needed page is available only in English, read it, explain in the user's language and identify the language fallback.

## 4. Recover from missing indexes or pages

At the 2026-11-04 check, both production `llms-md.txt` URLs returned HTTP 404; both Android SDK pages and the Chinese docs home returned HTTP 200. Treat this as a dated observation, then try the appropriate entry for the current task.

When the index returns 404 or is inaccessible:

1. Report the lookup limitation briefly. Continue from the known official SDK page or developer home above, using their actual navigation links. HTML is a useful fallback when clean Markdown is unavailable.
2. Use a site-restricted official-docs search if navigation does not expose the needed topic. Open and verify the result before relying on it; preserve the user's language where available.
3. Continue verified SDK work from the packaged references and target artifact. Leave only the missing onboarding/endpoint details pending. Keep the selected production documentation site and SDK version.

When an HTTP API page has no reachable schema, look for its linked official API definition or matching official SDK source. If field requirements remain unverified, list the exact missing facts and request that specific reference/access. Continue independent Android setup; defer the unsupported endpoint implementation.

Do not report a feature as available or absent solely because a documentation fetch failed. Avoid retrying an unchanged broken URL indefinitely or inventing nearby paths.

## 5. Match each question to its evidence

| Question | Evidence to inspect |
|---|---|
| Android signatures and behavior at 1.1.0 | Target AAR, matching public source and packaged [source baseline](source-baseline.md) |
| Supplied SDK-local `llms.txt` | Declared version, repository revision and relevant examples, reconciled with the target artifact; [known local-file drift](source-baseline.md#sdk-local-llmstxt) |
| Host capability at Tapro 1.0.5 | Pinned Host handling, deployment build and relevant device evidence |
| Dependency availability or proposed upgrade | Maven registry plus the target release notes; retain the user's pinned version during the current integration |
| Sandbox/onboarding/go-live preparation | Current official process pages and the deployment's approved onboarding details |
| HTTP request/response fields | Linked OpenAPI schema or equivalent official definition for that service/version |
| A web example disagrees with the pinned SDK | Compile against the target artifact, record both sources and use its valid signature; unresolved payment-behavior disagreements need confirmation |

For Sandbox preparation, look up terminal/merchant onboarding, the Tapro environment, app/API permissions, supported test instruments/scenarios and the required verification process. Credentials are configured locally. A missing account value does not prevent preparing code and build wiring.

## 6. Route related services explicitly

An integrator may extend a local terminal task with reporting, merchant-wide transaction history, Cloud payments or another language SDK. Determine which surface owns that requirement from current official documentation. Keep the Android local adapter using its verified methods, and propose the additional backend/SDK component only when the user requests that scope.

For example, Android 1.1.0 `query()` retrieves a known transaction using its original request ID or transaction ID. For “list a merchant's transactions,” locate an appropriate documented service and verify availability, permissions, merchant filters, pagination and environment before proposing calls. Keep any server-side credentials in the backend. Do not invent `TaplinkClient.listTransactions()` or treat `getTerminalInfo()` as transaction history.

The broader [SUNBAY integration skill](https://github.com/sunbay-developer/sunbay-integration-skill) can help with that discovery if already available. This reference is self-contained: reading another repository does not require installing it or appending its body to project-wide rules.

## 7. Deliver with traceable sources

State the target versions, the relevant public documentation links and the source/artifact used for signatures. Distinguish fetched evidence, packaged versioned evidence and any unresolved point. Use public page links for readers; keep machine-oriented Markdown/schema URLs in technical evidence when needed for implementation. Report successful code/build checks separately from credentials, terminal and payment testing.

# Feature flags: recommendation and implementation plan

Research date: **26 November 2026**. Deliverable for [MTOGO #111](https://github.com/EmilXavierVT/MTOGO/issues/111).

Recommend application-owned, global Boolean release flags, evaluated by the backend from environment values read once at startup. Configuration changes take effect through container recreation. This fits the chosen requirement to integrate unfinished work while controlling when users receive it, without operating a flag service. This is a project-specific design inference, not a measured performance or cost result.

Issue #111 delivers this cited report and follow-up specification. Runtime support, real flag enrollment, frontend changes and production configuration are follow-up work. The interface, endpoint, keys and configuration examples below are **proposals, not implemented contracts**. The team policies are recommendations; this report does not change the agreed Definition of Done.

## Context and alternatives

The backend uses Java 17 and Javalin 6.1.3, constructs dependencies in `App`, and registers routes centrally. It has no capability endpoint in the inspected routing. The actual frontend is Next.js 16.4.6 App Router with React 19.3.0, using same-origin `/api` requests and Bearer tokens. Sources: [backend dependencies](../pom.xml), [composition](../src/main/java/app/App.java), [routes](../src/main/java/app/server/Routing.java), [frontend dependencies](https://github.com/EmilXavierVT/MTOGO-Front/blob/2e0853f6c55007618bc9524bfda74468e14a3e12/package.json), [App Router API consumer](https://github.com/EmilXavierVT/MTOGO-Front/blob/2e0853f6c55007618bc9524bfda74468e14a3e12/app/%28pages%29/customer/api.js).

| Option | What it supplies | Assessment for MTOGO |
| --- | --- | --- |
| Application-owned environment configuration | Java can read named process environment values; defaults, parsing, injection and tests are ours to implement. [Java 17 `System.getenv`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#getenv(java.lang.String)) | Recommended first step: a small interface and immutable startup configuration; changes require recreation. |
| OpenFeature | Standard evaluation API and provider contract. Providers connect evaluation to a management system or local data. The Java SDK requires Java 11+, compatible with this Java 17 baseline. [Introduction](https://openfeature.dev/docs/reference/intro/), [Java SDK](https://openfeature.dev/docs/reference/sdks/server/java/) | Useful if interchangeable providers become valuable. The SDK itself supplies neither a persistent flag store nor an administration service; remote/live control still needs a suitable provider and source. |
| Togglz | A manually constructed `FeatureManager` outside Spring, with file and JDBC state repositories. Its optional servlet administration console requires authentication and integration. [Manual setup](https://www.togglz.org/documentation/advanced-config), [repositories](https://www.togglz.org/documentation/repositories), [console](https://www.togglz.org/documentation/admin-console) | A candidate for richer Java-local management. Pin a version and validate current APIs/Javalin integration before adoption; the website's older examples are not a tested integration here. |
| Self-hosted Unleash | A running management service using PostgreSQL. Its Java SDK fetches configuration and evaluates locally, with polling and a local backup cache. [Server configuration](https://docs.getunleash.io/deploy/configuring-unleash), [Java SDK](https://docs.getunleash.io/sdks/java) | Consider when live controls or targeting justify service/database operations. Cached last-known `true` can survive a control-plane outage; loss of connection is not an automatic OFF switch. |

Live controls, user targeting, percentage rollouts, experiments and an administration UI are deferred because the chosen first requirement is global availability with recreation acceptable. Revisit that choice when a concrete use case needs them. No library integration, benchmark, cost comparison or capacity assessment of the shared droplet was performed.

## Proposed backend and browser contract

Inject a narrow `FeatureFlags` interface with `boolean enabled(FeatureKey key)` into the application components that need it. Use named enum keys and one immutable environment-backed implementation created at startup; tests inject fixed on/off implementations. Avoid scattered `System.getenv` calls and string-key checks in business code.

For each new release flag, explicitly map an enum key to an environment name. Accept trimmed, case-insensitive `true` and `false`; missing, blank or invalid input means **OFF**. Identify invalid configuration by variable name in one startup warning per flag, without dumping environment values. `System.getenv` reads the process environment and returns `null` for an absent name; it does not load a `.env` file. The parser and defaults are application policy. [Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#getenv(java.lang.String))

When OFF, preserve the existing stable path for a replacement; omit an additive new UI action and deny its API capability. Keep authentication, role checks and applicable persisted-resource ownership checks first, then enforce the flag before writes, payment simulation or other side effects. A true flag grants no permissions. Propose `404` with the existing `{ "error": "..." }` shape for an authenticated, authorized request to a disabled new capability. Preserve existing `401`/`403` behavior in both states. This follows the current security/error boundaries; flag guards themselves remain follow-up work. [Authentication/authorization](../src/main/java/app/security/SecurityController.java), [ownership](../src/main/java/app/security/Ownership.java), [error handling](../src/main/java/app/App.java), [order service](../src/main/java/app/service/OrderService.java)

Propose authenticated `GET /api/features`, allowed for **all existing roles**, with `Cache-Control: no-store`. Return only an explicit allowlist of Boolean public capabilities, never raw environment configuration, credentials or targeting data. An illustrative response is `{ "exampleCapability": false }`; no real flag or public key is enrolled by this report. Role access remains enforced on each actual action, independent of this global snapshot. The existing all-role `/api/auth/me` route is the registration precedent. [Routes and roles](../src/main/java/app/server/Routing.java)

The Next.js client should fetch this endpoint at runtime after login and on entry to a relevant view, using the existing Bearer-token pattern and `cache: "no-store"`. Production Caddy already forwards frontend-domain `/api/*` to the backend; local Next rewrites serve the same URL convention. [Deployment routing](deployment.md#compose-og-watchtower), [API consumer](https://github.com/EmilXavierVT/MTOGO-Front/blob/2e0853f6c55007618bc9524bfda74468e14a3e12/app/%28pages%29/customer/api.js), [Next rewrite](https://github.com/EmilXavierVT/MTOGO-Front/blob/2e0853f6c55007618bc9524bfda74468e14a3e12/next.config.ts)

Only an explicit Boolean `true` for a recognized key enables a new browser capability. Unknown/missing keys, malformed data, unavailable endpoints and network failures leave new capabilities unavailable. Retain the existing login handling for expired tokens. Do not make already-shipped functionality depend on this new endpoint. Browser state can become stale after backend recreation: refresh on entry and handle an action being denied without assuming the earlier snapshot authorizes it. Version one promises no polling or instant updates to open tabs; the backend remains authoritative.

Avoid `NEXT_PUBLIC_*` release flags: Next.js inlines those values during build, so changing container environment values does not update an existing browser bundle. A runtime API supports the same frontend image across flag changes. [Next.js environment guide](https://nextjs.org/docs/app/guides/environment-variables#bundling-environment-variables-for-the-browser)

## Proposed configuration and rollout procedure

All example names below are illustrative and unimplemented. A later backend issue should add explicit forwarding under the existing `backend` service's `environment`, alongside its preserved database/JPA settings:

```yaml
services:
  backend:
    environment:
      MTOGO_FEATURE_EXAMPLE: "${MTOGO_FEATURE_EXAMPLE:-false}"
```

The corresponding private `.env` entry would start as `MTOGO_FEATURE_EXAMPLE=false`. Compose uses `.env` for interpolation; a value must be forwarded with `environment` or an appropriate container `env_file` to reach Java. Shell values can override interpolation-file values, so check the flag's effective source during rollout. Quoting keeps the environment value a string. [Compose interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/), [existing standalone Compose](../compose.yaml)

For a future standalone-stack exercise, with dependencies already healthy, pin `MTOGO_BACKEND_IMAGE` to the same recorded immutable registry digest and ensure that image is already available locally. After adding forwarding, use:

```sh
docker compose --env-file .env config --quiet
docker compose --env-file .env up --detach --wait --no-deps \
  --force-recreate --no-build --pull never backend
```

Change only the example flag between OFF → ON → OFF and repeat recreation and verification, retaining the same image digest throughout. `compose up` recreates containers to apply configuration; `compose restart` does not apply changed environment values. `--no-build` and `--pull never` keep this exercise independent of a new build or image update. Use quiet validation rather than printing the full resolved configuration, which includes secrets. [Compose up](https://docs.docker.com/reference/cli/docker/compose/up/), [Compose restart](https://docs.docker.com/reference/cli/docker/compose/restart/)

These commands describe the repository's standalone service only. The shared droplet uses `/home/jetty/deployment/docker-compose.yml`, containing other applications. Discover the actual Compose service name before adapting commands; preserve private configuration, schema-update settings, connections and container names. Follow the existing backup/rollback runbook, including coordination with the updater. Do not replace the shared file with this repository's Compose file or remove database volumes. No production commands were executed for this research. [Deployment and rollback runbook](deployment.md)

At each OFF/ON/OFF step, verify startup/health, the authenticated capability response, the stable/new behavior and direct API denial. Repeat the existing deployment smoke check and compare errors and latency through the existing Prometheus/Grafana dashboard; record the image digest, effective state and observations. Existing metrics provide status counts and p95 request duration, not flag-specific evaluation telemetry. [Smoke procedure](deployment.md#smoke-check-og-rollback), [monitoring](../README.md#overvågning-med-prometheus-og-grafana), [metrics implementation](../src/main/java/app/server/BackendMetrics.java)

Disabling a flag changes subsequent gated behavior. It does not undo schema changes, completed side effects or existing orders. Design expand/contract migrations and backward compatibility separately. Keep essential completion paths for in-flight workflows available; a switch-off that strands an accepted order is not an acceptable rollback plan.

## Recommended lifecycle within Takt

The repository uses short-lived issue branches and the `quick` → `wrapup` → rebase current `origin/main` → `gh deliver` workflow. With Takt enabled, Ready verifies and promotes the exact SHA, closes the delivered issue and synchronizes Done. Image publication assigns semantic versions; Watchtower follows `latest` with a documented 300-second interval. Application flags remain separate from `TAKT_ENABLED`. [Takt workflow](takt-workflow.md), [deployment](deployment.md#compose-og-watchtower)

The following are **recommended team policies**, not implemented process changes:

1. **Introduce disabled.** Record each flag's accountable person or team, owning repository, linked implementation/rollout issue, environment name, public key if any, OFF default and removal condition. Ship complete integration slices behind the guard, with an explicit stable path and compatible migrations.
2. **Verify enabled.** Exercise both states before delivery. An integration-slice issue may close with flag-off deployment only when its acceptance criteria explicitly allow that outcome. Takt delivery, builds and version tags prove integration; they do not prove a user feature is released.
3. **Release and monitor.** Require enabled demonstration evidence for the final product feature. If activation happens after a code issue auto-closes, track activation/demo in a separate rollout issue; do not silently treat code closure as final feature completion. Record the selected images, configuration and smoke/monitoring evidence.
4. **Remove.** After a stable rollout and the recorded removal condition, deploy the permanently enabled behavior and remove the conditional, obsolete path and obsolete tests. Keep returning `true` for an existing public capability while supported older clients still depend on that key; removing it early would make those clients hide the released feature. Remove the public key and environment forwarding only after their consumers are retired, accounting for supported older images and rollback needs before removing configuration or schema compatibility.

## Follow-up acceptance and ownership

**All tests below are future acceptance criteria. No runtime flag tests were executed for #111.** Each implementation issue must use its owning repository's Takt workflow separately.

| Scenario | Required evidence |
| --- | --- |
| ON/OFF and replacement | New behavior works ON; OFF preserves the existing stable path; additive capability stays unavailable. |
| Missing/invalid configuration | Missing, blank and invalid values resolve OFF; invalid input logs once at startup; explicit true/false parse as specified. |
| Direct API request while OFF | Authenticated, authorized request receives the proposed 404/error shape, with no writes or external side effects. |
| Security in both states | Missing/invalid token remains 401; wrong role and ownership violations remain 403, including bypass attempts. |
| Capability endpoint | Every existing role can authenticate; response contains only allowlisted Booleans and `no-store`; no secrets escape. |
| Browser unavailable/malformed capabilities | New controls remain unavailable; existing functionality and login handling still work; stale UI handles backend denial. |
| Mixed versions | New frontend/old backend fails closed for new capabilities; account for unknown-route 403 from older authorization middleware as well as 404. Old frontend/new backend keeps existing behavior. [Current authorization logic](../src/main/java/app/security/SecurityController.java) |
| Same-image configuration change | OFF → ON → OFF succeeds through recreation, health and smoke checks; backend image digest is unchanged, with no rebuild/pull. |

| Owning repository | Follow-up task | Dependency |
| --- | --- | --- |
| **MTOGO** | Implement injected configuration, forwarding, capability API and backend/security tests. | Agree the explicit endpoint/schema and parsing contract. |
| **MTOGO-Front** | Implement runtime capability consumption, fallback/stale-state behavior and UI/mixed-version tests. | Backend contract; preserve compatibility with an older backend. |
| **MTOGO**, coordinated with **MTOGO-Front** | Select the first real flag; specify rollout/ops, enabled demo, migration compatibility and cleanup criteria. | Both implementations and a concrete product feature. |

These are task specifications, without assigned people or newly created issues. The first real flag, exact service mapping and library integration behavior remain unverified until that work begins.

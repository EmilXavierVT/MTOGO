# Runtime feature flag foundation

Backend [#112](https://github.com/EmilXavierVT/MTOGO/issues/112) and
frontend [#17](https://github.com/EmilXavierVT/MTOGO-Front/issues/17) implement the foundation
from [research #111](feature-flags.md). [Customer feedback #60](customer-feedback.md) enrolls
the first product flag, `MTOGO_FEATURE_CUSTOMER_FEEDBACK`, default OFF, with the public key
`customerFeedback`. Existing ordering and delivery operations remain available. The research's
team-policy recommendations do not change the existing Definition of Done.

## Current contract

`GET /api/features` requires the existing Bearer token and permits every existing role.
It returns an explicitly public JSON object of Booleans with `Cache-Control: no-store`;
the application response includes `{"customerFeedback":false}` by default. Missing/invalid authentication receives the
existing `401` error. Raw environment names/values, credentials and internal flags are
never projected into the response. The Next.js client fetches this at runtime after its
role check; existing product UI does not wait for capability loading.

`FeatureCatalog.APPLICATION` explicitly registers typed `FeatureKey` values and a separate
public-name allowlist. Both collections are immutable; customer feedback is explicitly registered. Public names
must be lower camel case and refer to registered keys. Unknown process environment
variables do not enroll or expose flags automatically. Keys are typed values rather
than an empty enum, allowing isolated fixture definitions without a placeholder product
flag. Product issues declare named constants in the central catalog.

`EnvironmentFeatureFlags` reads each registered variable exactly once when the app is
created. Trimmed, case-insensitive `true` enables it; `false` disables it. Missing, blank
and invalid values are off. Blank/invalid values warn once per flag at startup, using
only its variable name; missing values are silent. Requests use the immutable snapshot.
Changing configuration requires a new application/container instance.

Inject `FeatureFlags` into a new service and call `flags.requireEnabled(key)` after
independent authentication, role and persisted-resource ownership checks, before gated
side effects. Customer feedback follows this pattern. A disabled guard throws `ApiException(404, "Feature unavailable")`, rendered
through the existing error handler as `{"error":"Feature unavailable"}`. A true flag
grants no permissions. Preserve the stable path for a replacement; an additive capability
must guard all relevant backend operations. Existing order/delivery routes have no new guards.

## Enrolling another product flag

1. Create linked feature issues and record an accountable owner, repository, issue,
   default-off value, public name if needed and removal condition. Follow Takt separately
   in each repository; enabled demonstration evidence is required for the whole feature.
2. Declare a named `FeatureKey("MTOGO_FEATURE_<NAME>")` constant and add it to
   `FeatureCatalog.APPLICATION`. Add a public mapping only when a browser needs it.
3. Inject the flags into the new service, add its guard and business tests for both states.
   Keep authorization/ownership checks independent and retain in-flight completion paths.
4. Register the agreed public name in MTOGO-Front's `app/features/catalog.ts`. Use
   `useFeature(name)` inside the existing `RequiredRole` boundary, retaining a stable
   fallback and handling backend refusal of a stale UI action. Deploy compatible backend
   support first. Missing/unknown values are off; do not use `NEXT_PUBLIC_*` release values.
5. Explicitly forward the variable under the existing backend service. A private `.env`
   entry alone is not passed into Java. This example is **illustrative and unregistered**:

   ```yaml
   services:
     backend:
       environment:
         MTOGO_FEATURE_EXAMPLE: "${MTOGO_FEATURE_EXAMPLE:-false}"
   ```

## Configuration changes and retirement

Customer feedback's forwarding and pre-activation migration are documented in its
[runbook](customer-feedback.md). Follow the [deployment runbook](deployment.md) with private backups and a
recorded immutable image digest. Local Java runs also need explicitly supplied process
variables; Java does not load Compose's `.env` file.

For the repository's standalone stack, keep `MTOGO_BACKEND_IMAGE` pinned to that digest,
with the image and dependencies already available. Change only the registered flag in
the private `.env`, then validate and recreate:

```sh
docker compose --env-file .env config --quiet
docker compose --env-file .env up --detach --wait --no-deps \
  --force-recreate --no-build --pull never backend
```

Shell variables take precedence over `.env` interpolation. Check the effective flag
source without printing resolved secrets. `docker compose restart` does not apply changed
environment values. Verify off/on/off using the same digest, health, capability response,
guards, existing behavior, smoke checks and error/latency dashboards. No polling or
zero-downtime switch is promised.

The shared droplet uses `/home/jetty/deployment/docker-compose.yml`. Discover its actual
service mapping and coordinate with Watchtower using the runbook. Preserve other apps,
private configuration, schema-update settings, container names and database volumes;
do not replace the shared file with the standalone one.

Disabling a flag does not reverse database changes or completed operations. Plan compatible
migrations. During retirement, keep returning true for a public capability while supported
older clients rely on it. Remove the key/configuration only after their consumers and
rollback needs have been addressed.

## Verification

Backend tests cover parsing, once-only reads/warnings, immutable snapshots, unknown keys,
catalog validation, public allowlisting, real HTTP authentication for all roles and role/
ownership checks before a guarded test handler's side effects. No product feature is
enrolled by the fixture catalog. Existing application tests cover unflagged behavior.

Frontend tests cover true/false and unknown/malformed values, old-backend 403/404 and 5xx,
network/JSON failure, timeout, token/route changes, obsolete requests, cancellation and
session expiry. New capabilities fail closed while existing content remains available;
an actual 401 clears both session stores, removes private content and returns to login.

Each product issue adds its own business on/off tests and same-image container recreation
demonstration. See [customer feedback](customer-feedback.md) for the first integration.

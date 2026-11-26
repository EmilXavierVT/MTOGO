# Customer delivery feedback

Product issue: [MTOGO #60](https://github.com/EmilXavierVT/MTOGO/issues/60).
React integration: [MTOGO-Front #18](https://github.com/EmilXavierVT/MTOGO-Front/issues/18).

## Release flag

| Setting | Value |
| --- | --- |
| Accountable owner | @EmilXavierVT |
| Owning repository / issue | MTOGO / #60; UI in MTOGO-Front #18 |
| Backend environment | `MTOGO_FEATURE_CUSTOMER_FEEDBACK` |
| Default | `false`; missing, blank and invalid values remain off |
| Public capability | `customerFeedback` in authenticated `GET /api/features` |
| Removal condition | Approved stable activation, supported consumers migrated and rollback window closed |

The immutable startup configuration and guard use the [runtime foundation](feature-flags-runtime.md).
Off hides the new customer entry point/form and denies **both** feedback endpoints with
`404 {"error":"Feature unavailable"}`. Authentication, role and persisted order ownership
are independent: missing/invalid login stays 401; a wrong role or another customer's order
stays 403. Normal customer order history, checkout and courier completion remain available.
Off does not remove saved feedback, change delivery state, or recalculate earned bonus.

## HTTP contract

Both routes require the order's authenticated CUSTOMER and return `Cache-Control: no-store`:

- `GET /api/customer/orders/{orderId}/feedback`: 200 with the persisted delivery/courier
  and `feedback: null` before submission, or the saved ratings afterwards.
- `POST /api/customer/orders/{orderId}/feedback`: submit a JSON object containing
  `foodRating`, `overallExperienceRating`, `courierRating`, each a JSON integer 1–5.
  Returns 201 with the same view and its saved feedback ID. The request supplies no
  authoritative customer, courier or delivery identity; extra identity fields are ignored.

Example response:

```json
{
  "orderId": "00000000-0000-0000-0000-000000000001",
  "deliveryId": "00000000-0000-0000-0000-000000000002",
  "courierId": "00000000-0000-0000-0000-000000000003",
  "courierName": "Assigned courier",
  "feedback": {
    "id": "00000000-0000-0000-0000-000000000004",
    "foodRating": 5,
    "overallExperienceRating": 4,
    "courierRating": 3
  }
}
```

400 rejects invalid UUIDs, malformed/trailing JSON, missing/non-integer ratings and values
outside 1–5. 404 also covers a missing order. 409 covers premature feedback, missing or
incomplete delivery and a second submission. Both order and delivery must be DELIVERED,
with a recorded delivery time. The courier comes from that delivery. No comment or moderation
workflow is introduced. Responses contain no bonus, credentials or full user entities.

Submission locks the order for the entire transaction, then checks stored ownership, the
flag, delivery eligibility, duplicates and ratings before persisting all three values.
Concurrent submissions produce one saved result and one 409. The existing one-to-one
delivery constraint backs uniqueness; `ck_feedback_ratings` backs the range. A failed insert
rolls back all feedback and permits a later explicit retry. Feedback never writes orders,
deliveries, commission or earned bonus.

## Deployment and activation

Deliver compatible backend support before the frontend via each repository's Takt workflow.
The standalone Compose file explicitly forwards the flag, defaulting false. The shared
droplet needs the same forwarding under its actual backend service; preserve its existing
private environment, JPA update override, other applications and volumes. A `.env` entry
alone is insufficient.

Fresh schemas include the range/uniqueness constraints. Hibernate schema update does not
promise to add checks to existing tables. Before first activation, take a private backup
and apply [the idempotent PostgreSQL migration](migrations/issue-60-feedback-constraints.sql)
with `psql -v ON_ERROR_STOP=1`. It refuses invalid/duplicate legacy data instead of changing
it. Keep the flag off if preflight fails. The migration adds no columns and preserves older
image compatibility; do not remove constraints/data to roll back the flag.

Follow the [deployment runbook](deployment.md) and [same-image recreation procedure](feature-flags-runtime.md#configuration-changes-and-retirement).
Pin an immutable image, coordinate Watchtower, change only the registered flag and recreate
only the actual backend service. Verify OFF → ON → OFF, health, the authenticated capability,
direct API guards, normal ordering/completion and error/latency dashboards. Enabling and then
disabling preserves already saved ratings. An open browser handles stale API denial; re-enter
the page to refresh capabilities. No polling or zero-downtime activation is promised.

The application default remains OFF. Production was explicitly activated on 26 November
2026 through [rollout #113](operations/customer-feedback-production-2026-11-26.md), including
same-image rollback and authenticated browser/API verification. On retirement, retain
`customerFeedback: true` while supported older clients still consume it.

## Verification

Run `./scripts/check.sh quick`. Set `MTOGO_COURIER_TEST_JDBC_URL` only to an explicitly
disposable PostgreSQL database named `mtogo_courier_test` to include concurrent submissions,
constraint bypass and rollback tests; Ready CI uses PostgreSQL 17 for these tests.
HTTP tests cover both flags, roles/ownership, invalid JSON/types/bounds, every premature
order state, incomplete deliveries, persisted courier identity, duplicates, reload and unchanged
bonus. Frontend tests cover the enabled form/result, hidden controls, stale switch-off,
expired login, duplicate recovery, lost response recovery, order changes and entry links.

### Local demonstration, 26 November 2026

- `quick`: 117 backend tests passed with PostgreSQL 17, including concurrent requests,
  direct database constraint violations, insert rollback and three legacy migration cases.
  The shared Takt tool suite passed all 41 tests.
- The repository Dockerfile built successfully. OFF → ON → OFF used the same local image
  ID `sha256:b449f6edd701f18ca35555b3cb70912a0ddb54aec70cb52fcfa2c777741cfbf1`,
  recreated with `--pull=never` against an isolated disposable database.
- With OFF, a fictitious customer completed checkout, restaurant acceptance/readiness and
  courier claim/pickup/delivery. The capability was false and feedback GET/POST returned 404;
  another customer still received 403 and unauthenticated access 401.
- With ON, the browser followed the delivered-order link, submitted 5/4/3 for food/overall/
  the assigned courier and reloaded the same saved result. Duplicate submission returned 409.
- After returning OFF, GET/POST were denied, the browser hid both form and entry point,
  the order remained visible, one saved feedback row remained and the earned bonus stayed
  **1.42 DKK** throughout. All five existing deployment smoke checks passed locally.
- Frontend quick checks passed 96 tests, lint and TypeScript. The
  [saved-result screenshot](https://github.com/EmilXavierVT/MTOGO-Front/blob/main/docs/images/customer-feedback-enabled.jpg)
  uses only fictitious local data. This local demonstration did not change production;
  the subsequent production activation is recorded separately above.

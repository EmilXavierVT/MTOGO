# Buddets leveringsinterface

Backend [#57](https://github.com/EmilXavierVT/MTOGO/issues/57) og frontend
[#16](https://github.com/EmilXavierVT/MTOGO-Front/issues/16) udvider leveringsflowet
fra #55. Alle kald kræver `Authorization: Bearer <token>` med rollen `COURIER`.
Bud-ID kommer fra det validerede login, aldrig fra requesten. Svar har
`Cache-Control: no-store`.

| Kald | Resultat |
| --- | --- |
| `GET /api/courier/orders/available` | `200` med en liste; `[]` når ingen er ledige. Kun `READY_FOR_DELIVERY` uden en eksisterende tildeling, ældste ordre først og UUID som sekundær sortering. |
| `GET /api/courier/delivery/active` | `200` med eget aktive leveringsobjekt; `204` uden body når ingen er aktiv. Kun `CLAIMED` og `UNDER_DELIVERY`. |
| `POST /api/courier/orders/{orderId}/delivery/accept` | Eksisterende atomiske overtagelse fra #55; `201`. Højst ét bud pr. ordre og én aktiv levering pr. bud. |
| `POST /api/courier/orders/{orderId}/delivery/pickup` | Eksisterende handling fra #55; `200`. Kun eget bud og `CLAIMED` → `UNDER_DELIVERY`. |
| `POST /api/courier/orders/{orderId}/delivery/delivered` | Eksisterende handling fra #55; `200`. Kun eget bud og `UNDER_DELIVERY` → `DELIVERED`. |

Manglende/ugyldigt login giver `401`; forkert rolle eller en andens handling giver
`403`. Samtidig overtagelse, anden aktiv levering eller forkert tilstand giver
`409` med `{ "error": "..." }`. Læs oversigten igen efter handlinger og konflikter.
En ledig ordre er ikke en reservation.

## Læsekontrakt

Et element i den ledige liste:

```json
{
  "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "orderTime": "2026-11-26T12:00:00",
  "restaurant": { "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", "name": "Havnekøkkenet", "address": "Havnegade 10" }
}
```

Egen aktive levering:

```json
{
  "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "status": "CLAIMED",
  "restaurant": { "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", "name": "Havnekøkkenet", "address": "Havnegade 10" },
  "customerName": "Anna Jensen",
  "customerPhone": "12345678",
  "deliveryAddress": "Østerbrogade 12, 2. tv.",
  "claimedTime": "2026-11-26T12:10:00",
  "pickupTime": null
}
```

Kundeoplysninger er de gemte checkout-oplysninger fra ordren. Ledige ordrer
udleverer kun afhentningssted og ordreidentitet/-tidspunkt; kundens navn,
telefon og leveringsadresse er kun med i eget aktive leveringsobjekt.
Entiteter, loginoplysninger, provision og andre bud udleveres ikke.
Tidsfelterne er eksisterende backend-`LocalDateTime` i ISO-format uden offset.
Et umiddelbart POST-svar kan have nanosekundpræcision; PostgreSQL gemmer
mikrosekunder. HTTP-testen tillader derfor under én mikrosekunds forskel ved
første genindlæsning og kræver identiske værdier ved efterfølgende læsninger.
De eksisterende POST-svar beholder deres kontrakt: `id`, `orderId`, `courierId`,
`status`, `claimedTime`, `pickupTime`, `deliveryTime`.

## Verifikation

`CourierDeliveryIntegrationTest` bruger det rigtige HTTP-API med login og en
isoleret database. Den dækker udvalg/sortering, begrænset dataudlevering,
ejerskab, genindlæsning, afhentning/levering, konflikter og rollegrænser.
CI og Takt-verifikation kører disse tests mod en isoleret PostgreSQL 17-service.
Lokalt bruges H2, medmindre `MTOGO_COURIER_TEST_JDBC_URL` er sat som nedenfor.
De eksisterende `DeliveryServiceTest` og `DeliveryClaimConcurrencyTest` bruger
fortsat H2. HTTP-testene er ikke dokumentation for en PostgreSQL-samtidighedstest.
Ingen schemaændring er nødvendig for denne udvidelse.

```sh
./mvnw -Dtest=CourierDeliveryIntegrationTest test
./scripts/check.sh quick
```

En separat lokal PostgreSQL-kørsel bruger en disponibel database med navnet
`mtogo_courier_test` og testbrugeren/adgangskoden `courier_test`. Testene opretter
og sletter tabeller i denne database; forbindelsen må kun pege på testdatabasen.
De lokale check-scripts starter ikke Docker automatisk.

```sh
docker run --detach --rm --name mtogo-courier-test \
  --publish 127.0.0.1:18573:5432 \
  --env POSTGRES_DB=mtogo_courier_test \
  --env POSTGRES_USER=courier_test --env POSTGRES_PASSWORD=courier_test postgres:17
docker exec mtogo-courier-test pg_isready -U courier_test -d mtogo_courier_test
MTOGO_COURIER_TEST_JDBC_URL=jdbc:postgresql://localhost:18573/mtogo_courier_test \
  ./mvnw -Dtest=CourierDeliveryIntegrationTest test
docker stop mtogo-courier-test
```

Frontend viser kun næste gyldige handling, blokerer nye overtagelser under en
aktiv levering og henter begge læse-endpoints igen efter en handling, også ved
`409`. Ved ukendt netværksudfald skal oversigten genlæses før næste handling.
Kort og ruteplanlægning indgår ikke.

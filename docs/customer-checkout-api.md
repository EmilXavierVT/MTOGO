# Kundens checkout og ordrebekræftelse

B18 / [issue #52](https://github.com/EmilXavierVT/MTOGO/issues/52). Frontend starter checkout fra kurven og viser efter succes `/customer/orders/{orderId}`. Alle nedenstående API-kald kræver kundens bearer-token.

Webappen bruger sin eksisterende samme-origin `/api`-proxy til backend. Den tidligere CORS-regel for port 5173 er fjernet: den gav fejlstatus på Next.js-appens videresendte browserkald, selv når handleren allerede havde kørt. API'et giver ingen CORS-tilladelse til direkte kald fra en anden browser-origin. Integrationstestene sender en realistisk `Origin`-header gennem login og checkout.

## Opret ordre

`POST /api/customer/orders` modtager:

```json
{
  "restaurantId": "restaurantens UUID",
  "products": [{ "productId": "rettens UUID", "quantity": 2 }],
  "name": "Kundens navn",
  "phone": "12345678",
  "address": "Leveringsadresse",
  "acceptedTotal": 100.00,
  "simulatedPaymentResult": "APPROVED"
}
```

Priser og accepteret total er DKK inklusive moms. Frontend summerer i hele øre. Backend kontrollerer fortsat restaurant, antal, tilgængelighed og aktuelle priser før den lokale betalingssimulation.

`201` returnerer `id`, `status`, `total`, `orderTime`, `restaurant` (`id`, `name`, `address`), `customerName`, `customerPhone`, `deliveryAddress` og `lines`. Hver linje indeholder `productId`, `productName`, `quantity`, `unitPrice` og `lineTotal`. Succes giver `PENDING` (afventer restaurant), tømmer kurven og åbner ordrebekræftelsen. Ingen reel betaling foretages.

## Fejl og ny prisaccept

- `409` med `code: "PRICE_CHANGED"` indeholder `updatedTotal` og `products` med `productId`, `productName` og `unitPrice`. Frontend opdaterer de viste linjer og totalen. Kunden skal markere en ny prisaccept og aktivt indsende igen. Der sker ingen automatisk genbestilling; endnu en prisændring kræver endnu en accept.
- `409` for en utilgængelig ret eller `404` for en slettet ret indeholder `code: "PRODUCT_UNAVAILABLE"` og `productId`. Retten identificeres med navn i UI og kan fjernes fra kurven. Betaling er blokeret, indtil den berørte ret er fjernet.
- `402` betyder afvist betalingssimulation. Ingen ordre eller ordrelinjer gemmes. Kurv og leveringsoplysninger bevares, så kunden kan forsøge igen.
- `401`/`403` kræver en gyldig kundekonto. Validerings- og forbindelsesfejl vises i formularen. En forbindelsesfejl alene beviser ikke, om serveren nåede at gemme ordren.

Fejlobjekter beholder et læsbart `error`-felt. Formularen deaktiveres under et igangværende kald for at forhindre samtidige indsendelser fra samme visning.

## Genindlæs bekræftelse

`GET /api/customer/orders/{orderId}` returnerer samme ordresvar for den indloggede kundes egen ordre. En ukendt ordre og en anden kundes ordre giver begge `404`; andre roller giver `403`, og ugyldigt UUID giver `400`. Svar med ordreoplysninger bruger `Cache-Control: no-store`.

Bekræftelsessiden bruger kun GET. Den opretter aldrig en ny ordre ved genindlæsning og henter produktnavne, priser, antal og leveringsoplysninger fra det gemte ordresnapshot. Senere menu- og profilændringer påvirker derfor ikke disse oplysninger. Ordrestatus læses fra den eksisterende ordre. En samlet ordrehistorik tilhører B23 / #58.

## Verifikation

`CheckoutIntegrationTest` afprøver HTTP-kald mod Javalin og en isoleret H2-database med produktionsentiteterne: prisaccept, utilgængelige/slettede retter, afvist simulation, gemte snapshots, gentagne GET-kald og rolle-/ejerskabsgrænser. Kør med Java 17:

```sh
mvn --batch-mode --no-transfer-progress verify
```

Frontend verificeres med `npm test`, `npm run lint` og `npm run build`. Testene omfatter gentagne prisændringer, bevarede formularoplysninger, fjernelse af utilgængelige retter, samtidige klik, beløb i hele øre og genindlæsning af ordrebekræftelsen.

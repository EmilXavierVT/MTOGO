# CI og deployment

Backend lytter på **7070**, frontend på **3000**. Caddy afslutter HTTPS på
[API-domænet](https://mtogo.project-ice.dk) og [frontenddomænet](https://mtogofront.project-ice.dk).

## Hemmeligheder

`SECRET_KEY` er backendens JWT-signering, ikke en API-nøgle til en ekstern tjeneste.
Compose læser den fra `MTOGO_SECRET_KEY` i en privat `.env` uden for Git. Generér én
kryptografisk tilfældig værdi med mindst 32 bytes, og behold den mellem deployments.
Rotation gør eksisterende login-tokens ugyldige. Databasens adgangskode er separat.
`.env.example` viser variabelnavne; tomme hemmeligheder afvises ved Compose-validering.
Begræns adgang til den rigtige `.env` med filrettigheder 600.

## CI og publicering

1. Backend `Backend Build & Test` kører Java 17 `mvn clean verify`, gemmer test-,
   coverage- og PMD-rapporter, bygger produktionsimaget og starter det med PostgreSQL.
   Smoke-testen kræver HTTP 200 på `/` og 401 på `/api/auth/me` uden token.
2. `Code Quality & Security` kører MegaLinter. Begge backend-checks skal bestå for
   præcis samme main-commit, før image-workflowen må publicere.
3. Frontend `Frontend Build & Lint` bruger Node 24 ligesom Docker-imaget. Det kører
   lint, tests og build samt en test af produktionssidernes og API-proxyens routing.
   CI kører på PR'er og main; publicering kræver bestået main-CI.
4. Image-workflows checker det verificerede commit ud, publicerer `sha-<fuldt SHA>`
   og promoverer kun til `latest`, hvis committet stadig er aktuelt main. PR-runs
   kan ikke publicere. BuildKit-cache genbruges; publicerede images får SBOM.
5. `Publish Versioned Release` kontrollerer tag, main-tilhørsforhold og begge
   backend-checks og promoverer et eksisterende image ved digest. Det ændrer ikke
   Watchtowers valgte tag.

Main beskyttes i begge repos med opdateret branch, krævede GitHub Actions-checks,
én anden persons godkendelse, afvisning af forældede godkendelser og afklarede
reviewtråde. Reglerne gælder også administratorer; force-push og sletning afvises.
Backend kræver `Backend Build & Test` og `Code Quality & Security`; frontend kræver
`Frontend Build & Lint`. Check-kilden er GitHub Actions, app-id 15368.

## Compose og Watchtower

Repoets Compose-fil starter en selvstændig MTOGO-stack. Sæt de to hemmeligheder i
`.env`, og kør `docker compose config --quiet` inden `docker compose up --detach --wait`.
Images hentes fra registry; filen bygger ikke et andet main-commit fra en Git-URL.
App-porte bindes som standard til loopback. Containerne har HTTP-healthchecks og
begrænset logrotation. Databasevolumen skal bevares ved almindelige deployments.

På den delte droplet ligger driftsfilen i `/home/jetty/deployment/docker-compose.yml`.
Den indeholder også andre applikationer og en eksisterende database; erstat den
ikke med repoets selvstændige Compose-fil. Backendens eksisterende schema-update
og databaseforbindelse skal bevares. Versionsspecifikke migrationer vurderes før rollout.

Watchtower følger de faktiske container-navne:

```text
morgendagens supaapi mtogo-frontend mtogo-backend
```

MTOGO-services har eksplicit `container_name: mtogo-backend` og `mtogo-frontend`.
En Compose-servicealias alene opfylder ikke Watchtowers navnefilter. Polling er
300 sekunder. Ændringer i Compose-miljø, porte eller navne skal anvendes ved
recreation; et image-pull distribuerer ikke den nye Compose-konfiguration.

Caddy bruger `mtogo-backend:7070` til API-domænet. På frontenddomænet sendes
`/api/*` til samme backend og øvrige requests til `mtogo-frontend:3000`.
Frontend-imagets Next-rewrite har også `http://mtogo-backend:7070` som build-default;
`BACKEND_URL` kan vælges som Docker build-argument til andre miljøer.

## Smoke check og rollback

Kør efter opdatering:

```sh
python3 scripts/smoke-deployment.py \
  --api https://mtogo.project-ice.dk \
  --frontend https://mtogofront.project-ice.dk
```

Alle fem checks skal bestå, inklusive login-siden og API-routen gennem frontend.
Scriptet opretter ingen konti og ændrer ingen data. Kontrollér også image-revisioner
på de to containere mod de valgte Git-SHA'er; en grøn procescheck alene viser ikke,
at et nyt image er hentet.

Før konfigurationsændringer gemmes en privat kopi af Compose, Caddy og `.env` samt
databasebackup og tidligere image-digests. Ved rollback standses updateren for at
undgå en samtidig opdatering. Gendan den tidligere konfiguration og de kendte image-
digests, recreate kun MTOGO-services, validér/reload Caddy og gentag smoke-checken.
Gendan ikke en databasebackup automatisk ved et kodeproblem: afklar først ændringer
og data siden backup. Watchtower cleanup gør lokale gamle images utilstrækkelige
som eneste rollback-kilde; behold registry-digests og konfiguration separat.

Upstream `containrrr/watchtower` er arkiveret. Planlæg vedligeholdelse af updateren
som en særskilt ændring; skift ikke til en ukendt fork som del af et almindeligt rollout.

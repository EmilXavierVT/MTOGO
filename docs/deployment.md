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

Backend og frontend bruger de samme verify-kommandoer lokalt og i CI. Backend
bruger Java 17 med Maven Wrapper, Checkstyle, JUnit, PMD og JaCoCo. Frontend bruger
Node 24 med lint, genererede routetyper, TypeScript, Vitest og produktionsbuild.
MegaLinter kan fortsat køres manuelt; Docker/PostgreSQL-smoke er separat.

Takt er opt-in. Før aktivering bevares PR-checknavnene og de eksisterende separate
main-publiceringsjobs. PR-runs publicerer ikke images. Branchbeskyttelse og
credentials ændres ikke af denne kildekodeændring.

Efter den dokumenterede [Takt-aktivering](takt-workflow.md#maintainer-cutover-after-team-validation)
kører verifikation på ready-branches, hvorefter det præcise SHA fast-forwardes til main.
Versioner bestemmes af issue-labels, og det samme image får sha- og versionstags.
Kun aktuelt main kan promoveres til latest, som Watchtower fortsat følger.
Den manuelle versionsworkflow genbruger samme digest og afviser overskrivning med
et andet image. Main-beskyttelse, tokenopsætning, retry og rollback er beskrevet
i Takt-vejledningen.

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

# MTOGO

Semesterprojekt: en webapplikation til bestilling og levering af mad med en dokumenteret Kanban-proces.

## Projektets aftaler

- [Projektplan og forretningsregler](docs/project-plan.md)
- [Kanban, kvalitetskrav og releases](docs/kanban.md)
- [Prioriteret backlog](docs/backlog.md)
- [Menuadministration i API](docs/owner-menu-api.md)
- [Kundens checkout og ordrebekræftelse](docs/customer-checkout-api.md)
- [CI, deployment og gendannelse](docs/deployment.md)
- [GitHub Projects](https://github.com/users/EmilXavierVT/projects/7)
- [Skabelon til flow review](docs/flow-reviews/template.md)

Backend bruger Java 17 og Maven. Frontend vedligeholdes i [MTOGO-Front](https://github.com/EmilXavierVT/MTOGO-Front).
CI kører tests og kvalitetskontroller. Docker/PostgreSQL-smoke er separat.
Main-workflowet publicerer images til Docker Hub; Watchtower opdaterer de navngivne containere på dropletten.
Backend lytter internt på port 7070. Se deploymentvejledningen for miljøværdier, smoke checks og rollback.

## Lokale checks og Takt

Kør ./scripts/check.sh quick under udvikling og ./scripts/check.sh verify før aflevering.
Scriptet vælger Java 17 på macOS og bruger den versionsbundne Maven Wrapper.

[Takt-vejledningen](docs/takt-workflow.md) beskriver opsætning, valgfri pre-push-hook,
issuebaseret versionsmærkning og tidsmåling i In progress og Review.
Automatisk Takt-aflevering kræver særskilt aktivering efter teamets PR-validering.

## Overvågning med Prometheus og Grafana

Kopiér `.env.example` til `.env`, hvis filen ikke findes. Udfyld
`MTOGO_DB_PASSWORD`, `MTOGO_SECRET_KEY` (mindst 32 bytes til JWT) og
`MTOGO_GRAFANA_PASSWORD`. Hemmeligheder gemmes kun lokalt i den ignorerede `.env`.
Byg backenden fra denne checkout og start PostgreSQL, Prometheus og Grafana:

```sh
docker compose -f compose.yaml -f compose.monitoring.yaml up -d --build grafana
```

- Kontrollér, at `mtogo-backend` er **UP** under [Prometheus Targets](http://localhost:9090/targets).
- Åbn [MTOGO Backend](http://localhost:3001/d/mtogo-backend/mtogo-backend) i Grafana.
  Log ind som `admin` med `MTOGO_GRAFANA_PASSWORD`. Adgangskoden bruges ved
  første oprettelse af Grafana-volumenet; en senere ændring i `.env` ændrer ikke
  en eksisterende Grafana-brugers adgangskode.
- Kald `curl http://localhost:7070/` flere gange over mindst 30 sekunder.
  Dashboardet viser scrape-status, HTTP-kald pr. status, p95-svartid og JVM-heap.
  Ved andre porte i `.env` bruges de tilsvarende adresser.
- Kontrollér indsamlingen direkte med
  `curl -G http://localhost:9090/api/v1/query --data-urlencode 'query=up{job="mtogo-backend"}'`.
  Resultatets værdi skal være `1`. Trafikgrafer kræver mindst to scrapes.

Prometheus scraper `/metrics` på backendens **interne** port 9464 hvert 15. sekund.
Porten publiceres ikke på værten, og det offentlige API har ingen metrics-rute.
HTTP-labels indeholder registrerede rutemønstre, fx `/api/restaurants/{restaurantId}/menus`,
og status/metode; konkrete ID'er, queryparametre og ukendte URL'er gemmes ikke.
Ukendte ruter samles under `unmatched`. Metrics starter kun, når
`MTOGO_METRICS_PORT` er sat, som i monitoring-overlayet.

Prometheus og Grafana er bundet til localhost, også hvis appens `MTOGO_BIND_IP`
ændres. Volumenerne `prometheus-data` og `grafana-data` bevarer målinger,
dashboards og brugere ved genstart; Prometheus gemmer som standard 15 dages data.
Det provisionerede dashboard styres af JSON-filen i `monitoring/grafana/dashboards`;
gem ændringer dér, da Grafana genindlæser filen ved opstart.
Stop med `docker compose -f compose.yaml -f compose.monitoring.yaml down`.
Undlad `down -v`, hvis data skal bevares, da det også sletter databasevolumenet.

Ved deployment bruges et publiceret backend-image, som indeholder metrics:
sæt `MTOGO_BACKEND_IMAGE` til det ønskede tag og brug `--no-build` i stedet for
`--build`. Frontenden kan startes samtidig ved at tilføje `frontend` til kommandoen.

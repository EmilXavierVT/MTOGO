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

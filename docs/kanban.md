# Kanban og kvalitet

## Board og pull

[MTOGO-board](https://github.com/users/EmilXavierVT/projects/7). Issues er den fælles backlog. Rækkefølgen i [backloggen](backlog.md) er pull-rækkefølge; afhængigheder og kapacitet skal også være opfyldt. Estimater er persontimer inklusive relevant implementering, tests, review og dokumentation.

| Kolonne | Politik | WIP-grænse |
| --- | --- | --- |
| Backlog | Prioriteret, men ikke nødvendigvis klar til start. | Ingen fast grænse |
| Ready | Acceptkriterier og næste skridt er forstået; afhængigheder er afsluttet eller ikke længere blokerende; typisk 2–6 persontimer. | Ingen fast grænse |
| In progress | Træk en klar opgave, når der er plads; aftal ansvarlig og registrer faktisk start. | 2 |
| Review | Ændringen er klar til andres review og relevante checks består. | 1 |
| Done | Hele Definition of Done er opfyldt; registrer faktisk afslutning. | Ingen fast grænse |

Aktivt WIP er højst 3 i alt, og begge kolonnegrænser skal samtidig overholdes. GitHub viser ikke nødvendigvis en håndhævet grænse; teamet håndhæver politikken.

Blokerede opgaver bliver i deres kolonne og tæller med. Brug `blocked`-label og skriv årsag, ansvarlig for næste skridt og hvad der kræves for at komme videre. Start ikke mere arbejde for at skjule en blokering. Hjælp med afslutning og review, når grænsen er nået.

## Prioritet og estimater

- P0: Komplet ordreflow, menuadministration, økonomi og nødvendig proces-/leveringsevne.
- P1: Feedback, engelsk og adminoversigt. De er planlagt og genovervejes først ved dokumenteret tidspres.

Et issue indeholder et lille resultat, acceptkriterier, afhængigheder, uge og foreløbigt estimat. Estimaterne er planlægning, ikke gennemført arbejde eller en garanti. Teamets konkrete arbejdsfordeling er åben. Der tildeles ikke personer automatisk.

## Trunk-based development og review

Arbejd på korte branches fra `main`, og integrer tilstræbt samme arbejdsdag. Store ændringer opdeles i demonstrerbare trin. Hver ændring går gennem en lille PR med mindst én anden persons godkendelse og bestået CI. Link issue og PR/commits, så sammenhængen er synlig.

Reviewkravet giver videndeling og kontrol af især adgang, økonomi, data og ordretilstande. Den lave review-WIP skal holde review kort. Hvis teamet senere ændrer politikken, dokumenteres årsag og konsekvens i et flow review. `main` holdes stabil og potentielt deploybar.

## Definition of Done

- Acceptkriterier er opfyldt og kan demonstreres, inklusive relevante fejltilfælde.
- Relevante automatiske tests og CI består.
- Backend og frontend bygger; lint/statisk analyse og coverage-resultater er tilgængelige.
- Mindst én anden persons review er godkendt; blokerende feedback er afklaret.
- Ændringen er integreret i `main`, linked til issue og tilgængelig i demomiljøet.
- Nødvendig dokumentation er opdateret.
- Afslutning og links til evidens er registreret på issue/board.

For afklarings- og procesopgaver er evidensen den godkendte aftale eller reviewnote. Der kræves ikke kunstige kodeændringer for at opfylde en kodeorienteret checkliste. Opgaver markeres ikke Done alene, fordi de er oprettet eller planlagt.

## CI og verifikation

CI skal automatisere build og tests for backend/frontend, lint/statisk analyse og coverage-rapportering. Der er ikke vedtaget en bestemt coverage-procent eller et bestemt analyseprodukt. Et eventuelt quality gate og undtagelser skal begrundes.

Prioriter tests af:

- Provision under, på og over 100/1.000 kr., korrekt afrunding og det accepterede fald omkring 950–1.000 kr.
- Bonus kun én gang ved levering; ingen provision/bonus ved afvisning.
- Rolle- og ejerskabsgrænser på backend.
- Tilladte og ugyldige ordreovergange.
- Samtidig overtagelse af samme levering og flere leveringer til samme bud.
- Prisændringer og utilgængelige retter ved checkout samt uforanderlig ordrehistorik.
- Feedbacks ejerskab, tidspunkt, interval og entydighed.

Database- og samtidighedsregler verificeres med relevante integrationstests mod PostgreSQL. Testene skal påvise adfærd; et antal tests eller coverage alene dokumenterer ikke reglerne.

## Flowmålinger og ugentlig gennemgang

- Cycle time: Fra første indgang i In progress til Done, inklusive review og blokeringer. Brug faktiske tidspunkter og oplys enhed; forløbet må ikke nulstilles ved blokering eller flytning.
- Throughput: Antal færdige issues pr. uge. Vis proces-/mødeopgaver separat fra funktioner/tekniske leverancer, så et ekstra møde ikke ligner øget produktleverance.
- WIP: Antal opgaver i In progress og Review, inklusive blokerede.

Brug boardets Start date og Done date til datoer, med issuehistorik/log til præcise tidspunkter. Target date er kun en prognose. Udfyld ikke faktiske datoer, cyklustider eller resultater på forhånd.

Hold et flow review på 30 minutter hver uge med fire deltagere (2 persontimer i alt). Gennemgå målinger, ældste aktive arbejde og blokeringer; vælg ét forbedringseksperiment og følg op på sidste uges eksperiment. Brug [skabelonen](flow-reviews/template.md) og link noten fra review-issuet. Mødedato og ansvarlig aftales af teamet.

## Deployment og releases

Docker Compose skal kunne starte backend, frontend og database med vedvarende databasedata. Hosting er uafklaret; et konkret mål og adgang er en første afklaringsopgave. Miljøværdier og hemmeligheder lægges uden for Git; dokumentation viser kun navne og ufølsomme eksempler.

Hver ugedemo får en versionsmærket release, fx `0.1.0`, `0.2.0` og `0.3.0`. Under 0.x bruges ny minorversion til et nyt funktionelt inkrement og patchversion til rettelser; praksis og eventuelle kompatibilitetsbrud beskrives i releasenoter. Versionsnumre oprettes først, når der faktisk findes en release.

Deployment startes manuelt gennem GitHub Actions efter bestået CI og skal bruge den præcise version, som er kontrolleret. Dokumenter start, smoke check, gendannelse/genudrulning af tidligere version og håndtering af vedvarende data. En tilbageførsel af kode må ikke antage, at databasedata kan nedgraderes automatisk.

Ved vejledning vises inkrement, versionsnummer, board, målinger, forbedringseksperiment, build/tests, statisk analyse, coverage og kendte mangler. Vis faktisk evidens; denne plan er ikke evidens for implementering.

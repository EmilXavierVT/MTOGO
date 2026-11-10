# Prioriteret backlog

Godkendt efter projektinterviewet den 10. november 2026. De tidligere issues er slettet. Denne backlog er oprettet ud fra de nye aftaler.

[GitHub Projects](https://github.com/users/EmilXavierVT/projects/7) · [Projektplan](project-plan.md) · [Kanban og Definition of Done](kanban.md)

## Kapacitet

38 opgaver er foreløbigt estimeret til **113 persontimer** ud af **180 persontimer**. De resterende **67 timer** er reserve til usikkerhed, ekstra samarbejde og rettelser. Estimater omfatter relevant implementering, tests, review og dokumentation; møder er angivet som samlet deltagertid. Ingen timer er registreret som faktisk forbrug.

| Uge | Estimeret arbejde | Ugekapacitet | Reserve |
| --- | --- | --- | --- |
| 1 | 58 timer | 60 timer | 2 timer |
| 2 | 37 timer | 60 timer | 23 timer |
| 3 | 18 timer | 60 timer | 42 timer |

Uge 1 har mindst reserve. Teamet skal kalibrere estimaterne ved opstart og reagere tidligt på blokeringer. Reserve er ikke ekstra scope. Uge 3 bruges også til stabilisering og uforudsete rettelser ud over de eksplicitte afslutningsopgaver.

## Pull-rækkefølge

Træk højest prioriterede, klarlagte arbejde med respekt for afhængigheder og WIP. B01/B02 afklarer hosting og arbejdsfordeling; B03 kan begynde uafhængigt. Disse tre står i Ready. Øvrige starter i Backlog. In progress, Review og Done er tomme ved oprettelsen.

P0 er kerneflow og nødvendige proces-/leveringskrav. P1 er feedback, engelsk og adminoversigt: planlagt, men de første kandidater til genovervejelse ved tidspres. Målugen er relativ; der er ikke opfundet kalenderfrister eller assignees.

| Issue | Resultat | Prioritet | Uge | Persontimer | Afhænger af |
| --- | --- | --- | --- | --- | --- |
| [B01 / #31](https://github.com/EmilXavierVT/MTOGO/issues/31) | Afklar hosting og adgang til demomiljø | P0 | 1 | 2 | — |
| [B02 / #32](https://github.com/EmilXavierVT/MTOGO/issues/32) | Aftal arbejdsfordeling og fælles pull-politik | P0 | 1 | 2 | — |
| [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33) | Etabler fælles backend- og React-byggemiljø | P0 | 1 | 3 | — |
| [B04 / #34](https://github.com/EmilXavierVT/MTOGO/issues/34) | Etabler PostgreSQL-konfiguration og første datamodel | P0 | 1 | 3 | [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33) |
| [B05 / #35](https://github.com/EmilXavierVT/MTOGO/issues/35) | Automatiser build og tests i GitHub Actions | P0 | 1 | 3 | [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33) |
| [B06 / #38](https://github.com/EmilXavierVT/MTOGO/issues/38) | Tilføj lint, statisk analyse og coverage-rapporter | P0 | 1 | 3 | [B05 / #35](https://github.com/EmilXavierVT/MTOGO/issues/35) |
| [B07 / #39](https://github.com/EmilXavierVT/MTOGO/issues/39) | Start backend, frontend og database med Docker Compose | P0 | 1 | 3 | [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33), [B04 / #34](https://github.com/EmilXavierVT/MTOGO/issues/34) |
| [B08 / #42](https://github.com/EmilXavierVT/MTOGO/issues/42) | Etabler første gentagelige deployment til demomiljø | P0 | 1 | 3 | [B01 / #31](https://github.com/EmilXavierVT/MTOGO/issues/31), [B05 / #35](https://github.com/EmilXavierVT/MTOGO/issues/35), [B07 / #39](https://github.com/EmilXavierVT/MTOGO/issues/39) |
| [B09 / #45](https://github.com/EmilXavierVT/MTOGO/issues/45) | Etabler manuelle, versionsmærkede releases | P0 | 1 | 4 | [B08 / #42](https://github.com/EmilXavierVT/MTOGO/issues/42) |
| [B10 / #40](https://github.com/EmilXavierVT/MTOGO/issues/40) | Opret realistiske startdata for roller, restauranter og menuer | P0 | 1 | 2 | [B04 / #34](https://github.com/EmilXavierVT/MTOGO/issues/34) |
| [B11 / #43](https://github.com/EmilXavierVT/MTOGO/issues/43) | Implementer login, registrering og backend-adgang | P0 | 1 | 4 | [B04 / #34](https://github.com/EmilXavierVT/MTOGO/issues/34), [B10 / #40](https://github.com/EmilXavierVT/MTOGO/issues/40) |
| [B12 / #46](https://github.com/EmilXavierVT/MTOGO/issues/46) | Lav fælles login og rollebaseret navigation i React | P0 | 1 | 2 | [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33), [B11 / #43](https://github.com/EmilXavierVT/MTOGO/issues/43) |
| [B13 / #47](https://github.com/EmilXavierVT/MTOGO/issues/47) | Implementer restaurantens menuadministration i API | P0 | 1 | 3 | [B04 / #34](https://github.com/EmilXavierVT/MTOGO/issues/34), [B11 / #43](https://github.com/EmilXavierVT/MTOGO/issues/43) |
| [B14 / #49](https://github.com/EmilXavierVT/MTOGO/issues/49) | Lav restaurantens menuredigering i React | P0 | 1 | 3 | [B12 / #46](https://github.com/EmilXavierVT/MTOGO/issues/46), [B13 / #47](https://github.com/EmilXavierVT/MTOGO/issues/47) |
| [B15 / #50](https://github.com/EmilXavierVT/MTOGO/issues/50) | Vis restauranter, menuer og kurv til kunden | P0 | 1 | 3 | [B12 / #46](https://github.com/EmilXavierVT/MTOGO/issues/46), [B13 / #47](https://github.com/EmilXavierVT/MTOGO/issues/47) |
| [B16 / #36](https://github.com/EmilXavierVT/MTOGO/issues/36) | Implementer lineær provisionsberegning med grænsetests | P0 | 1 | 2 | [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33) |
| [B17 / #51](https://github.com/EmilXavierVT/MTOGO/issues/51) | Implementer checkout med prisaccept, betaling og ordresnapshot | P0 | 1 | 4 | [B11 / #43](https://github.com/EmilXavierVT/MTOGO/issues/43), [B13 / #47](https://github.com/EmilXavierVT/MTOGO/issues/47), [B16 / #36](https://github.com/EmilXavierVT/MTOGO/issues/36) |
| [B18 / #52](https://github.com/EmilXavierVT/MTOGO/issues/52) | Lav kundens checkout og ordrebekræftelse | P0 | 1 | 3 | [B15 / #50](https://github.com/EmilXavierVT/MTOGO/issues/50), [B17 / #51](https://github.com/EmilXavierVT/MTOGO/issues/51) |
| [B19 / #53](https://github.com/EmilXavierVT/MTOGO/issues/53) | Implementer restaurantens ordreovergange og afvisning | P0 | 2 | 4 | [B17 / #51](https://github.com/EmilXavierVT/MTOGO/issues/51) |
| [B20 / #54](https://github.com/EmilXavierVT/MTOGO/issues/54) | Vis og behandl restaurantens ordrer i React | P0 | 2 | 3 | [B14 / #49](https://github.com/EmilXavierVT/MTOGO/issues/49), [B19 / #53](https://github.com/EmilXavierVT/MTOGO/issues/53) |
| [B21 / #55](https://github.com/EmilXavierVT/MTOGO/issues/55) | Implementer atomisk budtildeling og leveringsstatus | P0 | 2 | 4 | [B19 / #53](https://github.com/EmilXavierVT/MTOGO/issues/53) |
| [B22 / #57](https://github.com/EmilXavierVT/MTOGO/issues/57) | Lav buddets leveringsinterface | P0 | 2 | 3 | [B12 / #46](https://github.com/EmilXavierVT/MTOGO/issues/46), [B21 / #55](https://github.com/EmilXavierVT/MTOGO/issues/55) |
| [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58) | Vis kundens egen ordrehistorik og status | P0 | 2 | 3 | [B18 / #52](https://github.com/EmilXavierVT/MTOGO/issues/52), [B19 / #53](https://github.com/EmilXavierVT/MTOGO/issues/53), [B21 / #55](https://github.com/EmilXavierVT/MTOGO/issues/55) |
| [B24 / #59](https://github.com/EmilXavierVT/MTOGO/issues/59) | Optjen bonus én gang ved gennemført levering | P0 | 2 | 2 | [B16 / #36](https://github.com/EmilXavierVT/MTOGO/issues/36), [B21 / #55](https://github.com/EmilXavierVT/MTOGO/issues/55) |
| [B25 / #60](https://github.com/EmilXavierVT/MTOGO/issues/60) | Indsaml tre kundebedømmelser efter levering | P1 | 2 | 3 | [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58) |
| [B26 / #61](https://github.com/EmilXavierVT/MTOGO/issues/61) | Færdiggør dansk/engelsk UI og menu-fallback | P1 | 2 | 3 | [B14 / #49](https://github.com/EmilXavierVT/MTOGO/issues/49), [B18 / #52](https://github.com/EmilXavierVT/MTOGO/issues/52), [B20 / #54](https://github.com/EmilXavierVT/MTOGO/issues/54), [B22 / #57](https://github.com/EmilXavierVT/MTOGO/issues/57), [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58) |
| [B27 / #62](https://github.com/EmilXavierVT/MTOGO/issues/62) | Lav adminoversigt med læseadgang til alle ordrer | P1 | 2 | 2 | [B12 / #46](https://github.com/EmilXavierVT/MTOGO/issues/46), [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58) |
| [B28 / #63](https://github.com/EmilXavierVT/MTOGO/issues/63) | Verificer adgang, historik og samtidighed på tværs af flowet | P0 | 2 | 6 | [B14 / #49](https://github.com/EmilXavierVT/MTOGO/issues/49), [B18 / #52](https://github.com/EmilXavierVT/MTOGO/issues/52), [B20 / #54](https://github.com/EmilXavierVT/MTOGO/issues/54), [B22 / #57](https://github.com/EmilXavierVT/MTOGO/issues/57), [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58), [B24 / #59](https://github.com/EmilXavierVT/MTOGO/issues/59) |
| [B29 / #64](https://github.com/EmilXavierVT/MTOGO/issues/64) | Beregn bidrag, omkostningsscenarier og break-even | P0 | 3 | 4 | [B16 / #36](https://github.com/EmilXavierVT/MTOGO/issues/36), [B24 / #59](https://github.com/EmilXavierVT/MTOGO/issues/59) |
| [B30 / #37](https://github.com/EmilXavierVT/MTOGO/issues/37) | Etabler faktiske flowmålinger og evidenslog | P0 | 1 | 2 | [B02 / #32](https://github.com/EmilXavierVT/MTOGO/issues/32) |
| [B31 / #41](https://github.com/EmilXavierVT/MTOGO/issues/41) | Afhold flow review og dokumenter eksperiment – uge 1 | P0 | 1 | 2 | [B30 / #37](https://github.com/EmilXavierVT/MTOGO/issues/37) |
| [B32 / #56](https://github.com/EmilXavierVT/MTOGO/issues/56) | Forbered og dokumenter ugens inkrement/demo – uge 1 | P0 | 1 | 2 | [B06 / #38](https://github.com/EmilXavierVT/MTOGO/issues/38), [B09 / #45](https://github.com/EmilXavierVT/MTOGO/issues/45), [B14 / #49](https://github.com/EmilXavierVT/MTOGO/issues/49), [B18 / #52](https://github.com/EmilXavierVT/MTOGO/issues/52) |
| [B33 / #44](https://github.com/EmilXavierVT/MTOGO/issues/44) | Afhold flow review og dokumenter eksperiment – uge 2 | P0 | 2 | 2 | [B30 / #37](https://github.com/EmilXavierVT/MTOGO/issues/37), [B31 / #41](https://github.com/EmilXavierVT/MTOGO/issues/41) |
| [B34 / #65](https://github.com/EmilXavierVT/MTOGO/issues/65) | Forbered og dokumenter ugens inkrement/demo – uge 2 | P0 | 2 | 2 | [B09 / #45](https://github.com/EmilXavierVT/MTOGO/issues/45), [B20 / #54](https://github.com/EmilXavierVT/MTOGO/issues/54), [B22 / #57](https://github.com/EmilXavierVT/MTOGO/issues/57), [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58), [B24 / #59](https://github.com/EmilXavierVT/MTOGO/issues/59) |
| [B35 / #48](https://github.com/EmilXavierVT/MTOGO/issues/48) | Afhold flow review og dokumenter eksperiment – uge 3 | P0 | 3 | 2 | [B30 / #37](https://github.com/EmilXavierVT/MTOGO/issues/37), [B33 / #44](https://github.com/EmilXavierVT/MTOGO/issues/44) |
| [B36 / #68](https://github.com/EmilXavierVT/MTOGO/issues/68) | Forbered og dokumenter ugens inkrement/demo – uge 3 | P0 | 3 | 2 | [B29 / #64](https://github.com/EmilXavierVT/MTOGO/issues/64), [B37 / #66](https://github.com/EmilXavierVT/MTOGO/issues/66), [B38 / #67](https://github.com/EmilXavierVT/MTOGO/issues/67) |
| [B37 / #66](https://github.com/EmilXavierVT/MTOGO/issues/66) | Stabiliser og smoke-test den samlede demoversion | P0 | 3 | 6 | [B09 / #45](https://github.com/EmilXavierVT/MTOGO/issues/45), [B25 / #60](https://github.com/EmilXavierVT/MTOGO/issues/60), [B26 / #61](https://github.com/EmilXavierVT/MTOGO/issues/61), [B27 / #62](https://github.com/EmilXavierVT/MTOGO/issues/62), [B28 / #63](https://github.com/EmilXavierVT/MTOGO/issues/63) |
| [B38 / #67](https://github.com/EmilXavierVT/MTOGO/issues/67) | Saml driftsvejledning, procesrefleksion og udvidelsespunkt | P0 | 3 | 4 | [B09 / #45](https://github.com/EmilXavierVT/MTOGO/issues/45), [B28 / #63](https://github.com/EmilXavierVT/MTOGO/issues/63), [B31 / #41](https://github.com/EmilXavierVT/MTOGO/issues/41), [B33 / #44](https://github.com/EmilXavierVT/MTOGO/issues/44), [B35 / #48](https://github.com/EmilXavierVT/MTOGO/issues/48) |

## Acceptkriterier

Hvert issue har også disse kriterier på GitHub. Den fælles [Definition of Done](kanban.md#definition-of-done) gælder for alle relevante leverancer.

### B01 – Afklar hosting og adgang til demomiljø

Vælg det konkrete mål for den aftalte Docker Compose-løsning.

- [ ] Teamets valgte Linux-server/VM eller aftalte alternativ er dokumenteret med ansvarlig for adgang.
- [ ] Forudsætninger for netværk, deployment og vedvarende data er afklaret; ingen udgifter eller hemmeligheder antages eller publiceres.
- [ ] Et manglende mål markeres som blokering for deployment, ikke som en gennemført opsætning.

GitHub: [B01 / #31](https://github.com/EmilXavierVT/MTOGO/issues/31)

### B02 – Aftal arbejdsfordeling og fælles pull-politik

Gør teamets arbejdsform konkret uden at antage faste makkerpar.

- [ ] Alle fire har aftalt samarbejdsform, reviewansvar og hvordan ansvar vælges ved pull.
- [ ] WIP 2 i In progress og 1 i Review samt politik for blokeringer er forstået.
- [ ] Aftalen, mødetid for ugentligt flow review og faktisk startdato dokumenteres.

GitHub: [B02 / #32](https://github.com/EmilXavierVT/MTOGO/issues/32)

### B03 – Etabler fælles backend- og React-byggemiljø

Giv alle samme reproducerbare udgangspunkt med den aftalte stack.

- [ ] Eksisterende Java-skelet bevares, og Javalin/Hibernate/PostgreSQL samt React kan bygges med dokumenterede kommandoer.
- [ ] JDK, Node og buildværktøj er ensrettet mellem udvikling og planlagt CI; versionsvalg er begrundet.
- [ ] Frontend kan kalde en ufølsom health-rute; konfiguration indeholder ingen hemmeligheder.

GitHub: [B03 / #33](https://github.com/EmilXavierVT/MTOGO/issues/33)

### B04 – Etabler PostgreSQL-konfiguration og første datamodel

Understøt konti, restauranter, menuer, ordrer, leveringer og økonomi med Hibernate.

- [ ] Relationer og ejerskab for én rolle pr. konto og én restaurant pr. restaurantkonto er dokumenteret.
- [ ] Databasen kan oprettes gentageligt fra et tomt miljø; startdata holdes adskilt fra brugerdata.
- [ ] Ordresnapshots, leveringstildeling og entydige økonomiposteringer har en dokumenteret datamodel.

GitHub: [B04 / #34](https://github.com/EmilXavierVT/MTOGO/issues/34)

### B05 – Automatiser build og tests i GitHub Actions

Kontroller små ændringer automatisk før integration.

- [ ] PR-checks bygger backend/frontend og kører relevante automatiske tests.
- [ ] En bevidst fejl giver et fejlet check, og en rettelse giver grønt resultat.
- [ ] Kommandoer og CI-resultater er linked; et tomt testkatalog fremstilles ikke som testdækning.

GitHub: [B05 / #35](https://github.com/EmilXavierVT/MTOGO/issues/35)

### B06 – Tilføj lint, statisk analyse og coverage-rapporter

Gør kvaliteten synlig uden at opfinde et ikke-aftalt coverage-mål.

- [ ] React-lint og valgt Java-analyse kører i CI med dokumenteret konfiguration.
- [ ] Coverage-rapport fra faktiske tests er tilgængelig som artifact/resultat.
- [ ] Eventuelle gates og undtagelser er begrundet; ingen bestemt procent antages som et krav.

GitHub: [B06 / #38](https://github.com/EmilXavierVT/MTOGO/issues/38)

### B07 – Start backend, frontend og database med Docker Compose

Giv teamet en fælles startkommando.

- [ ] En ren opstart giver backend, React-frontend og PostgreSQL med dokumenterede trin.
- [ ] Databaseoplysninger kommer fra ekstern miljøkonfiguration; eksempelkonfiguration indeholder kun ufølsomme pladsholdere.
- [ ] Databasedata overlever genstart af containere, og readiness/health kan kontrolleres.

GitHub: [B07 / #39](https://github.com/EmilXavierVT/MTOGO/issues/39)

### B08 – Etabler første gentagelige deployment til demomiljø

Gør et kontrolleret inkrement tilgængeligt i det valgte demomiljø.

- [ ] Det aftalte mål kan starte den samme Compose-løsning med miljøspecifik konfiguration.
- [ ] Deploymenttrin, version og smoke check er dokumenteret og reproduceret.
- [ ] Lagring og genudrulning er beskrevet; faktisk demo-URL registreres først, når den findes.

GitHub: [B08 / #42](https://github.com/EmilXavierVT/MTOGO/issues/42)

### B09 – Etabler manuelle, versionsmærkede releases

Deploy præcis den version, der har bestået CI.

- [ ] GitHub Actions kan manuelt deploye en verificeret release/tag efter bestået CI.
- [ ] SemVer-praksis under 0.x og release-noter er dokumenteret.
- [ ] Genudrulning af tidligere kodeversion er demonstreret, og databasekompatibilitet/datahåndtering er beskrevet uden automatisk antaget nedgradering.

GitHub: [B09 / #45](https://github.com/EmilXavierVT/MTOGO/issues/45)

### B10 – Opret realistiske startdata for roller, restauranter og menuer

Gør alle roller og ejerskabsgrænser demonstrerbare.

- [ ] Startdata omfatter kunde, restaurantejer, bud og admin samt mindst to restauranter og to bude til adgangs-/samtidighedstest.
- [ ] Hver restaurantkonto er tilknyttet én restaurant; hver konto har præcis én rolle.
- [ ] Startdata kan indlæses gentageligt; adgangskoder lagres ikke i klartekst eller som reelle credentials i repoet.

GitHub: [B10 / #40](https://github.com/EmilXavierVT/MTOGO/issues/40)

### B11 – Implementer login, registrering og backend-adgang

Giv de fire roller fælles login og sikre ejerskabsgrænser.

- [ ] Kunder kan registrere sig; øvrige roller oprettes gennem startdata.
- [ ] Backend identificerer brugeren sikkert, beskytter credentials og afviser uvedkommende rolle/ejerskab.
- [ ] Tests viser både tilladt adgang og afvist adgang til en anden kundes/restaurantens data; admin kan ikke ændre forretningsdata.

GitHub: [B11 / #43](https://github.com/EmilXavierVT/MTOGO/issues/43)

### B12 – Lav fælles login og rollebaseret navigation i React

Gør de fire interfaces tilgængelige gennem samme webapp.

- [ ] Kunden kan registrere sig og logge ind; øvrige startdatakonti kan logge ind.
- [ ] Navigation følger rollen, og logout/forkert login håndteres tydeligt.
- [ ] Backend er fortsat autoritet for adgang, også ved direkte API-kald.

GitHub: [B12 / #46](https://github.com/EmilXavierVT/MTOGO/issues/46)

### B13 – Implementer restaurantens menuadministration i API

Lad ejeren vedligeholde sin egen menu.

- [ ] Ejeren kan oprette og ændre retters navn, beskrivelse og pris samt markere dem utilgængelige.
- [ ] Danske felter og valgfrie engelske felter gemmes med fælles pris/tilgængelighed; prisgrundlag og validering dokumenteres.
- [ ] En anden restaurant eller en forkert rolle kan ikke redigere menuen; gamle ordrelinjer ændres ikke.

GitHub: [B13 / #47](https://github.com/EmilXavierVT/MTOGO/issues/47)

### B14 – Lav restaurantens menuredigering i React

Giv restaurantejeren et anvendeligt menuinterface.

- [ ] Ejeren kan oprette/redigere retter, pris og tilgængelighed samt tilføje engelsk navn/beskrivelse.
- [ ] Valideringsfejl vises, og tomme valgfrie oversættelser accepteres.
- [ ] Kun egen restaurant vises; billeder, tilvalg og kampagner indgår ikke.

GitHub: [B14 / #49](https://github.com/EmilXavierVT/MTOGO/issues/49)

### B15 – Vis restauranter, menuer og kurv til kunden

Gør kunden klar til at bestille fra én restaurant.

- [ ] Kunden kan vælge restaurant, se tilgængelige retter og ændre antal i kurven.
- [ ] Kurven håndhæver én restaurant pr. ordre og viser prisgrundlag og total tydeligt.
- [ ] Utilgængelige retter kan ikke nybestilles; eksisterende kurvdata skal stadig kontrolleres af checkout.

GitHub: [B15 / #50](https://github.com/EmilXavierVT/MTOGO/issues/50)

### B16 – Implementer lineær provisionsberegning med grænsetests

Beregn præcis den vedtagne dynamiske provision uden ekstra gebyr.

- [ ] Satsen er 6 % til og med 100 kr., 19/300 - x/30000 mellem 100 og 1.000 kr. og 3 % fra 1.000 kr., på netbeløbet.
- [ ] Decimalberegning og dokumenteret afrunding til øre bruges; satsen afrundes ikke tidligt.
- [ ] Tests dækker 0, under/på/over 100 og 1.000, negative input samt 250→13,75; 950→30,08 og 1.000→30,00 kr.

GitHub: [B16 / #36](https://github.com/EmilXavierVT/MTOGO/issues/36)

### B17 – Implementer checkout med prisaccept, betaling og ordresnapshot

Opret en korrekt ordre efter godkendt lokal betalingssimulation.

- [ ] Backend genkontrollerer restaurant, antal, priser og tilgængelighed; ændret total kræver ny kundeaccept.
- [ ] Ordren gemmer varer/priser/antal, kundeoplysninger, netgrundlag og provision uafhængigt af senere ændringer.
- [ ] Godkendt simulation giver Afventer restaurant; afvist betaling giver ingen betalt ordre. Ingen ekstern integration eller SMS bygges.

GitHub: [B17 / #51](https://github.com/EmilXavierVT/MTOGO/issues/51)

### B18 – Lav kundens checkout og ordrebekræftelse

Lad kunden gennemføre den første bestilling.

- [ ] Kunden angiver navn, telefon og leveringsadresse og ser totalen før betaling.
- [ ] Opdateret pris skal accepteres eksplicit; utilgængelige retter og afvist simulation vises forståeligt.
- [ ] Succes viser den oprettede ordre med restaurant og status, og genindlæsning ændrer ikke historikken.

GitHub: [B18 / #52](https://github.com/EmilXavierVT/MTOGO/issues/52)

### B19 – Implementer restaurantens ordreovergange og afvisning

Lad restauranten acceptere, afvise og klargøre ordrer.

- [ ] Kun egen restaurant kan ændre Afventer restaurant til Accepteret/Afvist og Accepteret til Klar til afhentning.
- [ ] Afvisning registrerer simuleret tilbagebetaling uden opkrævet provision eller optjent bonus.
- [ ] Ugyldige overgange, uvedkommende og gentagne afslutningskald håndteres konsistent; relevant adfærd er testet.

GitHub: [B19 / #53](https://github.com/EmilXavierVT/MTOGO/issues/53)

### B20 – Vis og behandl restaurantens ordrer i React

Giv ejeren et lille arbejdsinterface til ordreflowet.

- [ ] Ejeren ser egne ordrer med varer og aktuel status.
- [ ] Kun gyldige handlinger for accept, afvisning og klar til afhentning tilbydes.
- [ ] Fejl og opdateret status vises; API-adgangsgrænser bevares.

GitHub: [B20 / #54](https://github.com/EmilXavierVT/MTOGO/issues/54)

### B21 – Implementer atomisk budtildeling og leveringsstatus

Fordel ledige leveringer sikkert og gennemfør dem i korrekt rækkefølge.

- [ ] Bud kan overtage en ledig leveringsklar ordre; højst ét bud pr. ordre og én aktiv levering pr. bud.
- [ ] Kun det tildelte bud markerer Afhentet og derefter Leveret med konsistente tidspunkter.
- [ ] PostgreSQL-integrationstests viser konkurrerende overtagelser af samme ordre og to forskellige ordrer til samme bud.

GitHub: [B21 / #55](https://github.com/EmilXavierVT/MTOGO/issues/55)

### B22 – Lav buddets leveringsinterface

Lad buddet vælge og gennemføre sin levering.

- [ ] Buddet ser ledige leveringsklare ordrer og kan overtage én.
- [ ] Egen aktive levering viser nødvendige kunde-/adresseoplysninger og gyldige afhentnings-/leveringshandlinger.
- [ ] Konflikt ved samtidig overtagelse vises; kort og ruteplanlægning indgår ikke.

GitHub: [B22 / #57](https://github.com/EmilXavierVT/MTOGO/issues/57)

### B23 – Vis kundens egen ordrehistorik og status

Gør ordreflowet synligt for kunden i webappen.

- [ ] Kunden kan se egne ordrelinjer, gemte beløb og aktuel status gennem hele forløbet.
- [ ] Afvisning og simuleret tilbagebetaling vises tydeligt.
- [ ] Senere pris-/menuændringer ændrer ikke historikken, og andre kunders ordrer er utilgængelige.

GitHub: [B23 / #58](https://github.com/EmilXavierVT/MTOGO/issues/58)

### B24 – Optjen bonus én gang ved gennemført levering

Knyt bonus til den tildelte, gennemførte levering.

- [ ] Bonus er 20 % af ordrenes afrundede provision, afrundet konsekvent til øre, og tilhører det tildelte bud.
- [ ] Kun Leveret udløser optjening; afvisning eller uafsluttet levering giver ingen bonus.
- [ ] Gentagne eller konkurrerende afslutningskald giver ikke dobbelt optjening; beregning adskilles fra faktisk lønudbetaling.

GitHub: [B24 / #59](https://github.com/EmilXavierVT/MTOGO/issues/59)

### B25 – Indsaml tre kundebedømmelser efter levering

Afslut kundeoplevelsen med den aftalte feedback.

- [ ] Kunden på en leveret ordre kan én gang bedømme mad, samlet oplevelse og konkret bud med 1–5.
- [ ] Backend afviser forkert kunde, for tidlig feedback, værdier uden for intervallet og dubletter.
- [ ] En enkel React-form viser resultatet; feedback ændrer ikke bonus og kræver ikke kommentarer/moderation.

GitHub: [B25 / #60](https://github.com/EmilXavierVT/MTOGO/issues/60)

### B26 – Færdiggør dansk/engelsk UI og menu-fallback

Gør den samlede brugerrejse tilgængelig på begge sprog.

- [ ] Faste webtekster er samlet og findes på dansk og engelsk med sprogvalg.
- [ ] Restaurantens engelske navn/beskrivelse vises, når feltet findes; ellers vises dansk for det manglende felt.
- [ ] Begge sprog bruger samme pris/tilgængelighed, og en komplet ordre kan demonstreres på begge.

GitHub: [B26 / #61](https://github.com/EmilXavierVT/MTOGO/issues/61)

### B27 – Lav adminoversigt med læseadgang til alle ordrer

Giv admin den aftalte afgrænsede visning.

- [ ] Admin ser alle ordrer og deres aktuelle status.
- [ ] Backend afviser adgangen for andre roller og giver ikke admin nye ændringsrettigheder.
- [ ] Konto-/rolleadministration og større ledelsesdashboard indgår ikke.

GitHub: [B27 / #62](https://github.com/EmilXavierVT/MTOGO/issues/62)

### B28 – Verificer adgang, historik og samtidighed på tværs af flowet

Vis at det samlede ordreflow holder sine vigtigste regler.

- [ ] En fuld kunde→restaurant→bud-rejse er verificeret med korrekt provision, bonus og gemte beløb.
- [ ] Tests demonstrerer ejerskabsgrænser, ugyldige overgange, prisændringer, betalingsafvisning og restaurantafvisning.
- [ ] Relevante PostgreSQL-tests dækker konkurrerende tildeling og entydig bonus; CI-evidens er linked.

GitHub: [B28 / #63](https://github.com/EmilXavierVT/MTOGO/issues/63)

### B29 – Beregn bidrag, omkostningsscenarier og break-even

Vurder økonomien med ærlige og efterprøvelige antagelser.

- [ ] Eksempler viser provision, bonus og resterende bidrag, herunder 100→6,00/1,20/4,80 kr.
- [ ] Antagne faste og variable omkostninger er tydeligt markeret, og break-even samt følsomhed kan genberegnes.
- [ ] Der konkluderes ikke økonomisk bæredygtighed alene ud fra 20 %-loftet; ingen antagelser fremstilles som faktiske virksomhedsdata.

GitHub: [B29 / #64](https://github.com/EmilXavierVT/MTOGO/issues/64)

### B30 – Etabler faktiske flowmålinger og evidenslog

Mål processen fra første aktive opgave.

- [ ] WIP, cycle time fra første In progress til Done inklusive review/blokering og ugentlig throughput har dokumenterede datakilder/enheder.
- [ ] Start/Done udfyldes kun ved faktiske hændelser; præcise tidspunkter kan spores i historik/log.
- [ ] Proces-/mødeopgaver rapporteres særskilt fra produktleverancer; ingen historiske resultater opfindes.

GitHub: [B30 / #37](https://github.com/EmilXavierVT/MTOGO/issues/37)

### B31 – Afhold flow review og dokumenter eksperiment – uge 1

Gennemgå ugens faktiske flow og vælg en konkret forbedring.

- [ ] 30 minutters review med fire deltagere er afholdt; faktisk dato og deltagere registreres.
- [ ] WIP, throughput, cycle times, ældste aktive arbejde og blokeringer er linked til faktisk evidens.
- [ ] Ét eksperiment har forventning, måling, ansvarlig og opfølgning; tidligere eksperiment følges op, hvis det findes.

GitHub: [B31 / #41](https://github.com/EmilXavierVT/MTOGO/issues/41)

### B32 – Forbered og dokumenter ugens inkrement/demo – uge 1

Vis fungerende adfærd og dokumenteret proces ved vejledning.

- [ ] Et faktisk fungerende inkrement og dets version kan demonstreres; mangler beskrives ærligt.
- [ ] Board, flow review/eksperiment og CI-build/tests/statisk analyse/coverage vises med de tilgængelige faktiske resultater.
- [ ] Release/demo-noter forklarer implementeret/udskudt scope og prioriteter; en plan alene markeres ikke som gennemført demo.

GitHub: [B32 / #56](https://github.com/EmilXavierVT/MTOGO/issues/56)

### B33 – Afhold flow review og dokumenter eksperiment – uge 2

Gennemgå ugens faktiske flow og vælg en konkret forbedring.

- [ ] 30 minutters review med fire deltagere er afholdt; faktisk dato og deltagere registreres.
- [ ] WIP, throughput, cycle times, ældste aktive arbejde og blokeringer er linked til faktisk evidens.
- [ ] Ét eksperiment har forventning, måling, ansvarlig og opfølgning; tidligere eksperiment følges op, hvis det findes.

GitHub: [B33 / #44](https://github.com/EmilXavierVT/MTOGO/issues/44)

### B34 – Forbered og dokumenter ugens inkrement/demo – uge 2

Vis fungerende adfærd og dokumenteret proces ved vejledning.

- [ ] Et faktisk fungerende inkrement og dets version kan demonstreres; mangler beskrives ærligt.
- [ ] Board, flow review/eksperiment og CI-build/tests/statisk analyse/coverage vises med de tilgængelige faktiske resultater.
- [ ] Release/demo-noter forklarer implementeret/udskudt scope og prioriteter; en plan alene markeres ikke som gennemført demo.

GitHub: [B34 / #65](https://github.com/EmilXavierVT/MTOGO/issues/65)

### B35 – Afhold flow review og dokumenter eksperiment – uge 3

Gennemgå ugens faktiske flow og vælg en konkret forbedring.

- [ ] 30 minutters review med fire deltagere er afholdt; faktisk dato og deltagere registreres.
- [ ] WIP, throughput, cycle times, ældste aktive arbejde og blokeringer er linked til faktisk evidens.
- [ ] Ét eksperiment har forventning, måling, ansvarlig og opfølgning; tidligere eksperiment følges op, hvis det findes.

GitHub: [B35 / #48](https://github.com/EmilXavierVT/MTOGO/issues/48)

### B36 – Forbered og dokumenter ugens inkrement/demo – uge 3

Vis fungerende adfærd og dokumenteret proces ved vejledning.

- [ ] Et faktisk fungerende inkrement og dets version kan demonstreres; mangler beskrives ærligt.
- [ ] Board, flow review/eksperiment og CI-build/tests/statisk analyse/coverage vises med de tilgængelige faktiske resultater.
- [ ] Release/demo-noter forklarer implementeret/udskudt scope og prioriteter; en plan alene markeres ikke som gennemført demo.

GitHub: [B36 / #68](https://github.com/EmilXavierVT/MTOGO/issues/68)

### B37 – Stabiliser og smoke-test den samlede demoversion

Gør slutdemoen reproducerbar og ret de vigtigste tilbageværende fejl.

- [ ] Release-kandidaten kan deployes og gennemføre kerneforløbet fra et kendt datagrundlag.
- [ ] Fejl prioriteres inden for tidsbudgettet; scopeændringer aftales og dokumenteres.
- [ ] CI, genstart med vedvarende data, roller og engelsk/dansk er kontrolleret med linked evidens.

GitHub: [B37 / #66](https://github.com/EmilXavierVT/MTOGO/issues/66)

### B38 – Saml driftsvejledning, procesrefleksion og udvidelsespunkt

Gør projektets beslutninger, resultater og begrænsninger efterprøvelige.

- [ ] README forklarer faktisk build, tests, start, deployment, version og kendte mangler.
- [ ] Procesrefleksion sammenholder faktiske målinger og eksperimenter uden at opfinde historik.
- [ ] Modulgrænser og fremtidig klageudvidelse er beskrevet; klagebehandling er ikke implementeret.

GitHub: [B38 / #67](https://github.com/EmilXavierVT/MTOGO/issues/67)

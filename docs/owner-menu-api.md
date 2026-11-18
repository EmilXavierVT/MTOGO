# Menuadministration i API

Send `Authorization: Bearer <token>` fra `/api/auth/login`. Alle ruter kræver rollen `RESTAURANT_OWNER`.

| Metode og rute | Svar |
| --- | --- |
| `GET /api/owner/menus` | `200` og en liste over ejerens menuer. Hver menu har `id`, `name` og `products`. Listen indeholder også utilgængelige retter. En ejer uden menuer får `[]`. |
| `POST /api/owner/menus/{menuId}/products` | `201` og den nye ret med dens `id`. |
| `PUT /api/owner/menus/{menuId}/products/{productId}` | `200` og den gemte ret med dens `id`. Hele rettens redigerbare indhold erstattes. |

Send `Content-Type: application/json` med oprettelser og ændringer. En ret har de samme felter i svarene samt sit `id`.

```json
{
  "nameDa": "Rugbrød",
  "descriptionDa": "Friskbagt rugbrød med smør",
  "nameEn": "Rye bread",
  "descriptionEn": null,
  "priceDkkInclVat": 89.95,
  "available": true
}
```

- `nameDa` og `descriptionDa` er påkrævede og skal indeholde tekst. Navne må højst være 255 tegn.
- `nameEn` og `descriptionEn` er valgfrie uafhængigt af hinanden. Tekst trimmes, og et tomt, udeladt eller `null` engelsk felt gemmes som `null`. Brug den danske tekst for hvert manglende engelsk felt, når menuen vises.
- `priceDkkInclVat` er et positivt JSON-tal i DKK inklusive moms. Prisen skal kunne angives i hele øre og må højst være `9999999999.99`. API'et afviser brøkdele af en øre. Checkout bruger beløbet eksklusive moms til provision.
- `available` er en påkrævet boolean. Brug `false` til at gøre retten utilgængelig og `true` til at gøre den tilgængelig igen.
- Fejlsvar har formatet `{"error":"..."}`. `400` betyder ugyldigt input, `401` manglende eller ugyldigt token, `403` forkert rolle eller ejer, og `404` ukendt menu eller en ret, der ikke findes i den valgte menu.

## Opgradering af en eksisterende PostgreSQL-database

Kør transaktionen mod den eksisterende database inden opstart af den opdaterede backend. Behold schema-update-indstillingen fra Compose (`-Djakarta.persistence.schema-generation.database.action=update`), så eksisterende data bevares ved opstart. På en tom database opretter Hibernate den nye struktur.

```sql
BEGIN;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS description_da text,
    ADD COLUMN IF NOT EXISTS name_en varchar(255),
    ADD COLUMN IF NOT EXISTS description_en text,
    ADD COLUMN IF NOT EXISTS available boolean NOT NULL DEFAULT true;

DO $upgrade$
BEGIN
    IF EXISTS (SELECT 1 FROM products WHERE price::text IN ('NaN', 'Infinity', '-Infinity')) THEN
        RAISE EXCEPTION 'Resolve non-finite product prices before converting to numeric(12,2)';
    END IF;
END;
$upgrade$;

ALTER TABLE products
    ALTER COLUMN price TYPE numeric(12, 2) USING round(price::numeric, 2);

COMMIT;
```

Opgraderingen bevarer retternes id, navn og menu. Ældre priser fortolkes som DKK inklusive moms og afrundes til øre. De eksisterende retter starter som tilgængelige, og de nye tekstfelter er `null`, indtil ejeren udfylder dem. En pris, der ligger uden for kolonnens interval efter afrunding, afbryder transaktionen.

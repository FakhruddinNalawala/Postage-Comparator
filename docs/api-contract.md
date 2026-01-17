## API & JSON Contracts

### Overview

This document defines the JSON schemas and REST endpoints for the postage comparator backend. All responses are JSON; timestamps are ISO-8601, currency is AUD.
<!-- Sendle integration is currently disabled. -->

### Core Schemas

#### OriginSettings

```json
{
  "postcode": "2000",          // Required, 4 digits
  "suburb": "Sydney",          // Required, non-blank
  "state": "NSW",              // Required, non-blank
  "country": "AU",             // Required, 2-letter country code
  "themePreference": "dark",   // Optional: "dark" | "light" | "sepia"
  "updatedAt": "2025-01-06T10:15:30Z"
}
```

#### ThemePreferenceRequest

```json
{
  "themePreference": "light"   // Optional: "dark" | "light" | "sepia"
}
```

#### Item

```json
{
  "id": "item-123",            // Server-generated
  "name": "T-shirt",           // Required, non-blank, unique
  "description": "Unisex cotton tee",
  "unitWeightGrams": 250       // Required, > 0
}
```

#### Packaging

```json
{
  "id": "pack-1",                  // Server-generated
  "name": "Small satchel",         // Required, non-blank, unique
  "description": "250 x 330mm satchel",
  "lengthCm": 25,                  // Required, > 0
  "heightCm": 33,                  // Required, > 0
  "widthCm": 7,                    // Required, > 0
  "internalVolumeCubicCm": 6000,   // Optional in requests; if <= 0 server will derive from L×H×W
  "packagingCostAud": 0.75         // Required, > 0
}
```

#### ShipmentItemSelection

```json
{
  "itemId": "item-123",
  "quantity": 3
}
```

#### WeightBracket

Used internally for rule-based pricing calculations. Defines weight ranges and corresponding prices for standard and express services.

```json
{
  "minWeightInclusive": 0.0,      // Minimum weight in kilograms (inclusive)
  "maxWeightInclusive": 0.25,      // Maximum weight in kilograms (inclusive)
  "priceStandard": 9.70,          // Price in AUD for standard/regular service
  "priceExpress": 12.70            // Price in AUD for express service
}
```

**Current weight brackets for AusPost (in kg):**
- 0 - 0.25 kg: Standard $9.70, Express $12.70
- 0.25 - 0.5 kg: Standard $11.15, Express $14.65
- 0.5 - 1.0 kg: Standard $15.25, Express $19.25
- 1.0 - 3.0 kg: Standard $19.30, Express $23.80
- 3.0 - 5.0 kg: Standard $23.30, Express $31.80

#### ShipmentRequest

```json
{
  "destinationPostcode": "3000",
  "destinationSuburb": "Melbourne",
  "destinationState": "VIC",
  "country": "AU",
  "items": [
    { "itemId": "item-123", "quantity": 3 },
    { "itemId": "item-456", "quantity": 1 }
  ],
  "packagingId": "pack-1",
  "isExpress": false
}
```

#### CarrierQuote

```json
{
  "carrier": "AUSPOST",             // "AUSPOST" | "RULES",
  "serviceName": "Standard",
  "deliveryEtaDaysMin": 2,
  "deliveryEtaDaysMax": 4,
  "packagingCostAud": 0.75,
  "deliveryCostAud": 8.95,
  "surchargesAud": 0.0,
  "totalCostAud": 9.70,
  "pricingSource": "AUSPOST_API",   // "AUSPOST_API" | "RULES",
  "ruleFallbackUsed": false,
  "rawCarrierRef": "QTE-12345"
}
```

#### QuoteResult

```json
{
  "totalWeightGrams": 1000,
  "weightInKg": 1.0,
  "volumeWeightInKg": 1.375,
  "totalVolumeCubicCm": 5500,
  "origin": {
    "postcode": "2000",
    "suburb": "Sydney",
    "state": "NSW",
    "country": "AU",
    "themePreference": "dark"
  },
  "destination": {
    "postcode": "3000",
    "suburb": "Melbourne",
    "state": "VIC",
    "country": "AU"
  },
  "packaging": {
    "id": "pack-1",
    "name": "Small satchel",
    "packagingCostAud": 0.75
  },
  "carrierQuotes": [
    {
      "carrier": "AUSPOST",
      "serviceName": "Parcel Post",
      "deliveryEtaDaysMin": 3,
      "deliveryEtaDaysMax": 5,
      "packagingCostAud": 0.75,
      "deliveryCostAud": 9.40,
      "surchargesAud": 0.5,
      "totalCostAud": 10.65,
      "pricingSource": "AUSPOST_API",
      "ruleFallbackUsed": false,
      "rawCarrierRef": "AUS-999"
    }
  ],
  "currency": "AUD",
  "generatedAt": "2025-01-06T10:16:05Z"
}
```

#### ErrorEnvelope

```json
{
  "error": {
    "code": "BAD_REQUEST",              // or "NOT_FOUND"
    "message": "Destination postcode must be 4 digits",
    "details": null,                    // reserved for future use
    "timestamp": "2025-01-06T10:16:15Z"
  }
}
```

### REST Endpoints

Base path: `/api`.

#### Settings

- `GET /api/settings/origin` → `OriginSettings` or 404
- `PUT /api/settings/origin` → `OriginSettings` (create/update)
- `PUT /api/settings/theme` → `OriginSettings` (update theme only)

**Validation rules:**
- `postcode`: Required, 4 digits
- `suburb`, `state`, `country`: Required, non-blank; `country` must be a 2-letter code
- `themePreference`: Optional; must be one of `dark`, `light`, `sepia` when provided

**Note:** The `SettingsService` also provides the following methods for internal use:
- `getAusPostApiKey()`: Returns the AusPost API key from environment variable `AUSPOST_API_KEY`
- `getAusPostWeightBrackets()`: Returns the weight brackets used to calculate AusPost quotes when the API is unavailable

#### Items

- `GET /api/items` → `Item[]`
- `POST /api/items` → create `Item` (returns 201 Created)
- `GET /api/items/{id}` → single `Item` (returns 404 if not found)
- `PUT /api/items/{id}` → update `Item` (returns 404 if not found)
- `DELETE /api/items/{id}` → delete `Item` (returns 204 No Content)

**Validation rules:**
- `name`: Required, must not be null or blank, must be unique
- `unitWeightGrams`: Required, must be greater than 0

On validation error, returns **400** with `ErrorEnvelope` (`code = "BAD_REQUEST"`).

#### Packaging

- `GET /api/packaging` → `Packaging[]`
- `POST /api/packaging` → create `Packaging` (returns 201 Created)
- `GET /api/packaging/{id}` → single `Packaging` (returns 404 if not found)
- `PUT /api/packaging/{id}` → update `Packaging` (returns 404 if not found)
- `DELETE /api/packaging/{id}` → delete `Packaging` (returns 204 No Content)

**Validation rules:**
- `name`: Required, must not be null or blank, must be unique
- `lengthCm`, `heightCm`, `widthCm`: Required, must be greater than 0
- `internalVolumeCubicCm`: Optional; if not provided or <= 0, will be auto-calculated as `lengthCm × heightCm × widthCm`
- `packagingCostAud`: Required, must be greater than 0

On validation error, returns **400** with `ErrorEnvelope` (`code = "BAD_REQUEST"`).

#### Quotes

- `POST /api/quotes`
  - Request: `ShipmentRequest`
  - Response: `QuoteResult` with `carrierQuotes` for AusPost. Each quote includes packaging cost and total cost (packaging + delivery + surcharges).

**Validation rules (request):**
- `destinationPostcode`: Required, 4 digits
- `destinationSuburb`, `destinationState`, `country`: Required, non-blank; `country` must be a 2-letter code
- `items`: Required, non-empty array
  - Each `ShipmentItemSelection`: `itemId` required/non-blank, `quantity` > 0
- `packagingId`: Required, non-blank

On validation error, returns **400** with `ErrorEnvelope` (`code = "BAD_REQUEST"`).

### Example Requests

Sample REST requests are available in `docs/api-examples.http` for use with VS Code or similar REST clients.

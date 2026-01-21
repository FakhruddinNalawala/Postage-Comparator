## Backend Implementation Todos (You)

### A. Persistence & Configuration

- [x] **A1 – Origin settings persistence**
  - Implemented `SettingsService` (`SettingsServiceImpl`) to load/save `OriginSettings` to a plain JSON `settings.json` file under `POSTAGE_DATA_DIR` (or default `~/.postage-comparator`), using atomic write-then-rename via `FileWriteUtils`.
  - Provides methods to retrieve API keys and IDs from environment variables: `getAusPostApiKey()`, `getSendleApiKey()`, `getSendleId()`.
  - Provides `getAusPostWeightBrackets()` for rule-based pricing with predefined weight brackets.
  - Validates postcode (required, 4 digits) and ensures `updatedAt` is always set to current time on update.

- [x] **A2 – Items persistence**
  - Implemented `ItemService` (`ItemServiceImpl`) backed by a JSON file with safe writes using `FileWriteUtils`, validation (name required, `unitWeightGrams` > 0, unique names), and full CRUD (`findAll` with optional `activeOnly` filter, `findById`, `create`, `update`, and `delete`).
  - Uses Java 21 patterns: `var` keyword, `.toList()`, improved null safety.

- [x] **A3 – Packaging persistence**
  - Implemented `PackagingService` (`PackagingServiceImpl`) backed by a JSON file with safe writes using `FileWriteUtils`, validation (name required, dimensions `lengthCm`, `heightCm`, `widthCm` > 0, `packagingCostAud` > 0, unique names), and full CRUD (`findAll` with optional `activeOnly` filter, `findById`, `create`, `update`, and `delete`).
  - Auto-calculates `internalVolumeCubicCm` from dimensions if not provided or <= 0.
  - Uses Java 21 patterns: `var` keyword, `.toList()`, improved null safety.

- [x] **A4 – Config loader**
  - Configuration is implemented via environment variables (Spring Boot automatically reads env vars):
    - `POSTAGE_DATA_DIR`: Data directory path (defaults to `~/.postage-comparator` if not set)
    - `AUSPOST_API_KEY`: AusPost API key for authentication
    <!-- Sendle integration is currently disabled -->
  - API base URLs are configured in `CarrierWebClientConfig`:
    - AusPost: `https://digitalapi.auspost.com.au`
    <!-- Sendle base URL disabled -->
  - Configuration is accessed via `SettingsService` methods (`getAusPostApiKey()`, `getSendleApiKey()`, `getSendleId()`) and directly in service implementations for data directory paths.
  - Feature flags are implicit: APIs are enabled when corresponding environment variables are set; rules-based pricing is always available as fallback.

### B. Pricing Engine & Rules

- [x] **B1 – Shipping domain helpers**
  - Implemented helpers in `QuoteServiceImpl`:
    - `calculateTotalWeight()`: Computes total weight from `ShipmentRequest` items + `Item` definitions
    - `getPackaging()`: Retrieves and validates packaging
    - `buildDestination()`: Builds `QuoteResult.Destination` from `ShipmentRequest`
    - `validateRequest()`: Validates `ShipmentRequest` (origin configured, items/packaging exist, quantities > 0)

- [x] **B2 – Rule-based pricing**
  - Implemented rule-based pricing using `WeightBracket` model:
    - `calculateAusPostRulesBasedQuote()`: Calculates AusPost quotes using weight brackets from `SettingsService.getAusPostWeightBrackets()`
    - Weight brackets define ranges (in kg) with standard and express prices
    - Selects appropriate price based on `isExpress` flag from `ShipmentRequest`
    - Uses `DeliveryEtaUtils` for ETA calculations
    - Sendle rule-based pricing returns a placeholder quote (99.99) indicating API failure (no published pricing formula)

- [x] **B3 – QuoteService orchestration**
  - Implemented `QuoteService` (`QuoteServiceImpl`) to coordinate rules and APIs:
    - For each carrier (AusPost), attempts live API calls when configured via environment variables.
    - On API failure or missing config, falls back to rules with `pricingSource="RULES"` and `ruleFallbackUsed=true`.
    - Builds a `QuoteResult` with `carrierQuotes[]`; each `CarrierQuote` includes packaging cost and total cost.
    - Uses `DeliveryEtaUtils` for ETA calculations based on service type, state, and postcode types.

### C. Carrier HTTP Clients

<!--
- [x] **C1 – SendleClient**
  - Implemented `trySendleApi()` method in `QuoteServiceImpl` using `WebClient` with configurable base URL and auth.
  - Maps internal request data to the Sendle quote API shape (sandbox endpoint).
  - Parses the response array into a `CarrierQuote` with proper error handling.
  - Uses Base64 encoding for authentication with `SENDLE_ID` and `SENDLE_API_KEY` from environment variables.
-->

- [x] **C2 – AusPostClient**
  - Implemented `tryAusPostApi()` method in `QuoteServiceImpl` using `WebClient` with configurable base URL and auth.
  - Maps internal request data to the AusPost quote API shape.
  - Parses the response into a `CarrierQuote` with proper error handling.
  - Uses `AUSPOST_API_KEY` from environment variables for authentication.

- [x] **C3 – Error handling & timeouts**
  - Implemented comprehensive error handling for carrier clients:
    - Handles `WebClientResponseException` for HTTP errors
    - Handles `WebClientException` for network/connection issues
    - Handles `RuntimeException`, `ClassCastException`, `NullPointerException`, `NumberFormatException` for parsing errors
  - All API failures return `null`, allowing `QuoteService` to fall back to rule-based pricing for that carrier.
  - `WebClient` instances are configured via `CarrierWebClientConfig` with base URLs for AusPost (`https://digitalapi.auspost.com.au`) and Sendle (`https://sandbox.sendle.com`).

### D. Validation & Error Model

- [x] **D1 – Request validation**
  - Implemented Bean Validation annotations on all request models:
    - `OriginSettings`: `@NotBlank` on postcode, suburb, state, country; `@Pattern("\\d{4}")` on postcode; `@Pattern("[A-Za-z]{2}")` on country
    - `Item`: `@NotBlank` on name; `@Positive` on `unitWeightGrams`
    - `Packaging`: `@NotBlank` on name; `@Positive` on dimensions and `packagingCostAud`
    - `ShipmentRequest`: `@NotBlank @Pattern("\\d{4}")` on destinationPostcode; `@NotBlank @Pattern("[A-Za-z]{2}")` on country; `@NotEmpty` on items; `@NotBlank` on packagingId
    - `ShipmentItemSelection`: `@NotBlank` on itemId; `@Positive` on quantity
  - Added `@Valid` annotations to all controller methods accepting request bodies (`ItemController`, `PackagingController`, `SettingsController`, `QuoteController`)
  - `GlobalExceptionHandler` now handles `MethodArgumentNotValidException` and `ConstraintViolationException`, returning structured `ErrorEnvelope` JSON with `{"error": {"code": "BAD_REQUEST", "message": "...", "timestamp": "..."}}` format
  - Service-level validation remains in place for business logic (duplicate names, etc.) and returns `BadRequestException` / `IllegalArgumentException`, also handled by `GlobalExceptionHandler`

- [x] **D2 – 404/consistency handling**
  - All missing resource scenarios return appropriate HTTP status codes:
    - `NotFoundException` → 404 with `{"error": {"code": "NOT_FOUND", "message": "...", "timestamp": "..."}}`
    - Used when: item/packaging ID not found in update operations, packaging/item referenced in quote request doesn't exist
    - `SettingsController.getOrigin()` returns 404 when origin settings are not configured (no exception, just empty result)
  - Additional error handling added to `GlobalExceptionHandler`:
    - `HttpMessageNotReadableException` → 400 (malformed JSON request body)
    - `MethodArgumentTypeMismatchException` → 400 (invalid parameter type, e.g., non-numeric ID)
    - `IllegalStateException` → 500 (internal/config errors like "Origin settings must be configured", IO failures)
  - All error responses follow consistent `ErrorEnvelope` structure as documented in `api-contract.md`

### E. Backend Tests (JUnit 5)

- [x] **E1 – Unit tests**
  - Comprehensive unit tests implemented:
    - `SettingsServiceImplTest`: Tests JSON load/save of `settings.json`, validation, corrupted file handling, weight brackets
    - `ItemServiceImplTest`: Tests JSON load/save of `items.json`, full CRUD operations, validation (null/blank inputs, duplicates, non-existent IDs, corrupted files)
    - `PackagingServiceImplTest`: Tests JSON load/save of `packagings.json`, full CRUD operations, validation, volume calculation
    - `DeliveryEtaUtilsTest`: Tests ETA calculations for express/standard, same state vs interstate, metro/rural combinations
    - `PostcodeUtilsTest`: Tests metro postcode detection with boundary values
    - `FileWriteUtilsTest`: Tests atomic write-then-rename pattern, directory creation, error handling
    - `QuoteServiceImplTest`: Tests request validation, rules-based pricing, AusPost API success/error paths, and `QuoteResult` composition

- [x] **E2 – Spring slice tests**
  - `ItemControllerTest`: Comprehensive `@WebMvcTest` covering all endpoints (GET, POST, PUT, DELETE), happy paths, error paths (400, 404), validation errors, blank/spurious IDs
  - `SettingsControllerTest`: `GET /origin` (200/404), `PUT /origin` validation and success cases
  - `PackagingControllerTest`: CRUD coverage with validation (400) and not-found (404) paths
  - `QuoteControllerTest`: Valid quote request (200), validation errors (400), and service failure (500)

- [ ] **E3 – Integration tests**
  - Add `@SpringBootTest` tests using a temporary data directory.
  - Exercise end-to-end flows for `/api/settings/origin`, `/api/items`, `/api/packaging`, and `/api/quotes`.
  - Optionally use WireMock to simulate Sendle/AusPost APIs and verify fallback behavior.

---

## Frontend & Dev-Experience Todos (AI-owned by default)

- [x] **F1 – Vue API client layer**
  - Implemented typed API wrappers for all `/api` endpoints (settings, items, packaging, quotes).

- [x] **F2 – Vue screens & components**
  - Built screens for origin settings, item management, packaging management, and the quote comparison flow.
  - Implemented modal-based CRUD for items and packaging, plus quote form/results display.

- [x] **F3 – Frontend tests**
  - Add Vitest tests for API client wrappers and key components.

- [x] **F4 – Documentation & examples**
  - Synced `docs/api-contract.md` and `docs/quotes-architecture.md` with implementation.
  - Added `docs/api-examples.http` for REST client examples.

- [ ] **F5 – Docker/dev ergonomics**
  - Refine Dockerfile and `docker-compose.yml` as carrier APIs and configuration evolve.
  - Optionally add Makefile or scripts for common dev tasks.

---

### G. Provider SPI & Connectors

- [ ] **G1 – Provider SPI foundation**
  - Add a `CarrierProvider` interface and `ProviderRegistry` for pluggable providers.
  - Define provider config model and enablement via env/config.

- [ ] **G2 – AusPost connector extraction**
  - Move AusPost API logic into an `AusPostProvider` implementation.
  - Reuse existing WebClient config and parsing helpers.

- [ ] **G3 – QuoteService orchestration via SPI**
  - Update `QuoteServiceImpl` to iterate enabled providers and preserve rules fallback.

- [ ] **G4 – SPI tests**
  - Update `QuoteServiceImplTest` to mock providers via registry.
  - Add unit tests for provider enablement and registry selection.
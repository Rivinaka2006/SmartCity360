# Energy Key-Resolution Diagnostics Design

## Intent

Make `KEY_CONFIGURATION_MISSING` failures diagnosable from server logs while preserving the existing exception codes, HTTP responses, cryptography, authorization, and key lookup behavior.

## Constraints

- Do not log key values, encoded key material, credentials, meter identifiers, key identifiers, or configuration property values.
- Do not change the database, Payara configuration, integration tooling, deployment, or runtime lifecycle.
- Do not change existing REST or service error contracts.
- Do not commit changes.

## Approaches

1. **Selected: log fixed stage and reason labels inside `PayaraKeyProvider`.** This is the narrowest boundary that knows which lookup failed and can report it without exposing lookup inputs.
2. Log only in `EnergyStreamServiceBean`. Rejected because the service receives only the shared error code and cannot distinguish HMAC from AES resolution failures.
3. Introduce new public exception or REST error codes. Rejected because it would change observable behavior and expose internal configuration detail beyond server logs.

## Design

`PayaraKeyProvider` will emit a fixed diagnostic message immediately before each `KEY_CONFIGURATION_MISSING` exception. Fixed labels distinguish meter HMAC key identifier, meter HMAC key value, current AES key identifier, envelope AES key identifier, and AES key value. Tests will capture log records and prove that the three live-processing failure stages are distinct while exception codes remain unchanged and no supplied identifier or value appears in log output.

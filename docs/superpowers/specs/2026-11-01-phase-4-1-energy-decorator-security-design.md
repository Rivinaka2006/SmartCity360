# Phase 4.1 Energy Decorator Security Design

## Intent

Implement authenticated, compressed, and AES-GCM-encrypted energy streams as an authentic Decorator Pattern while preserving the Jakarta EE EAR, human session security, and the existing 17-table schema.

## Approved approach

- Add nullable `credential_key_id` and `credential_key_version` columns to `energy_meters` through V005, with paired-presence, positive-version, and partial-unique constraints.
- Keep key bytes outside PostgreSQL and source control. Resolve Payara password aliases through the Payara MicroProfile Config `password` source behind a `KeyProvider` interface.
- Compose `EncryptionDecorator(CompressionDecorator(AuthenticationDecorator(RawEnergyDataStream)))`. Authentication runs before the raw component; transformations unwind as raw, compression, then encryption.
- Use one exact machine endpoint whose CSRF bypass is conditional on meter-authentication headers and absence of browser session cookies. HMAC validation remains mandatory in the EJB pipeline. Human history/decryption operations retain Jakarta Security, roles, sessions, and CSRF.
- Use a synchronized, bounded, expiring single-node nonce cache. It is deliberately not durable or cluster-wide.

## Non-goals

- No energy-reading parser or invented measurements.
- No additional tables, React work, JWT, Spring, or Phase 4.2 work.
- No permanent secret provisioning without a separate explicit approval.

## Security constraints

- HMAC-SHA256 signs a length-delimited canonical form containing meter code, credential version, timestamp, nonce, HTTP method, path, and SHA-256 payload digest.
- AES-256-GCM uses a fresh 96-bit nonce, a 128-bit tag, authenticated envelope metadata, and rotation-aware key IDs.
- Payload, envelope, decompression, timestamp, key, and replay limits fail closed with non-sensitive error codes.

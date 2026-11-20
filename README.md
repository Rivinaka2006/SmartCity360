# SmartCity360 — Installation and Execution Guide

This guide describes a local academic installation on Windows. The source ZIP
does not bundle Java, Maven, Payara, PostgreSQL, Node.js, npm, database data,
certificates, or private keys.

## 1. Project overview

SmartCity360 is a Jakarta EE 10 backend packaged as a Payara EAR and a separate
React/TypeScript development frontend. The frontend is not included in the EAR.
The backend implements five design patterns:

- Decorator — secured energy-stream processing
- Composite — hierarchical smart-city zones
- Chain of Responsibility — citizen complaint routing
- Flyweight — shared streetlight profiles
- Interpreter — versioned operational policy expressions

## 2. Prerequisites

Install these separately before proceeding:

- JDK 17. The Maven reactor sets `maven.compiler.release` to 17.
- Maven 3.9.x is recommended; the project does not include a Maven Wrapper or
  pin a Maven runtime version.
- Payara Server 6, compatible with the project's Jakarta EE 10 EAR.
- PostgreSQL 13 or later. No exact database version is pinned; the migrations
  use PostgreSQL features including `gen_random_uuid()` and advisory locks.
  PostgreSQL 18.3 is recorded in a project implementation plan.
- `psql`, installed with PostgreSQL or separately. Its location is passed to
  the database scripts.
- Node.js 24 or later and npm, as required by `frontend/package.json`.
- Windows PowerShell 5.1 or later.

Database creation requires a PostgreSQL administrator (or a role authorized to
create roles and databases). Installing/configuring Payara and importing a
certificate may require local administrative rights. Initial administrator
provisioning specifically requires an elevated local Windows Administrator
session. The scripts do not require access to another developer's computer.

## 3. Extract and verify

Extract the ZIP so the directory containing this guide is the project root.
That directory must contain `pom.xml`, `database\`, `backend\`, and
`frontend\`. The Maven reactor is rooted at `pom.xml`; its backend modules are
under `backend\smartcity360-common`, `backend\smartcity360-ejb`,
`backend\smartcity360-web`, and `backend\smartcity360-ear`.

Open PowerShell in that root directory. Use the extracted local paths below;
do not copy paths from another computer.

## 4. Database initialization: choose exactly one path

Choose **Option A** to restore the included demo snapshot, or **Option B** to
create a fresh database and initialize it with the project's migrations and
initial-admin provisioning script. Do not combine the paths.

### Option A — Quick setup using included demo database backup

The archive at
[`database/backups/SmartCity360_Full_2026-11-11.backup`](database/backups/SmartCity360_Full_2026-11-11.backup)
is a PostgreSQL custom-format dump created on PostgreSQL 18.3. It is intended
to contain the schema, migration ledger, and data. Restore it with PostgreSQL
18's `pg_restore` into a **new, empty** database. Do not copy the archive into
PostgreSQL's data directory; it is a dump archive, not a data-directory
snapshot.

PostgreSQL server roles and their passwords are not included in `pg_dump`.
Create the application login separately, then create the empty database owned
by that login. From PowerShell in the project root, first connect to the
maintenance database as a PostgreSQL administrator:

```powershell
$PgBin = 'C:\Program Files\PostgreSQL\18\bin'
& "$PgBin\psql.exe" -h localhost -p 5432 -U postgres -d postgres
```

At the `psql` prompt, if this login does not already exist, create it; set or
reset its password interactively. If it already exists, omit the `CREATE ROLE`
line:

```sql
CREATE ROLE smartcity360_app LOGIN;
\password smartcity360_app
\q
```

Use a password of your choice; do not put it in the command line or project
files. Confirm that a database named `smartcity360` does not already exist,
then run these commands from the project root. `createdb` and
`pg_restore` will prompt for the relevant PostgreSQL role passwords:

```powershell
$PgBin = 'C:\Program Files\PostgreSQL\18\bin'
$Archive = '.\database\backups\SmartCity360_Full_2026-11-11.backup'

& "$PgBin\createdb.exe" -h localhost -p 5432 -U postgres `
  --owner=smartcity360_app smartcity360
if ($LASTEXITCODE -ne 0) { throw 'Creating the empty database failed.' }

& "$PgBin\pg_restore.exe" -h localhost -p 5432 -U smartcity360_app `
  -d smartcity360 --no-owner --no-acl --exit-on-error $Archive
if ($LASTEXITCODE -ne 0) { throw 'Restoring the database archive failed.' }
```

Do not add `--clean` or `--create`: the restore must target the newly created
empty database without dropping or creating databases as part of the restore.
These connection settings match the project's local JDBC URL
`jdbc:postgresql://localhost:5432/smartcity360`; adjust host or port only if
your local PostgreSQL configuration differs. The archive restore does not
configure Payara: the PostgreSQL JDBC driver, `jdbc/smartcity360` connection
pool and JDBC resource, and credentials still need to be configured
separately as described in Section 6. External Payara TLS certificates, DB
pool settings, and external cryptographic keys are not contained in the
database backup.

**Do not run the migration runner or `Provision-InitialAdmin.ps1` on this
restored database.** The restore path includes the migration ledger and data;
the fresh-initialization scripts are for Option B only.

### Option B — Fresh database with migrations and a new admin

Follow Sections 5 and 7 for the mutually exclusive fresh-database path.
Section 6 describes Payara/JDBC configuration needed by the scripts and
application.

## 5. Create and migrate the database (Option B)

The migration runner applies schema changes to a database already created in
PostgreSQL; it does not create the database or PostgreSQL login. For a clean
local academic setup, connect to the PostgreSQL maintenance database as an
administrator:

```powershell
psql -h localhost -U postgres -d postgres
```

At the `psql` prompt, create a dedicated login and database. Set the role
password with `\password` so `psql` prompts for it instead of putting it in the
SQL text or command history:

```sql
CREATE ROLE smartcity360_app LOGIN;
\password smartcity360_app
CREATE DATABASE smartcity360 OWNER smartcity360_app;
\q
```

Use your locally selected password. The database owner needs schema/table
creation privileges for first-time migrations and normal application access.
Do not use the PostgreSQL superuser as the application's datasource account.

Before migrations, configure Payara's `jdbc/smartcity360` datasource as
described below. The runner reads the database URL, username, and password
from the Payara `domain1` configuration. It accepts:

- `-Mode`: `ValidateFiles`, `Baseline`, `Migrate`, or `Verify` (default:
  `Verify`).
- `-PayaraHome`: Payara installation directory.
- `-PsqlPath`: full path to `psql.exe`.

The absolute paths in the script are defaults only. Supply your local paths:

```powershell
.\database\scripts\Invoke-SmartCityMigrations.ps1 `
  -Mode Migrate `
  -PayaraHome '<PAYARA_HOME>' `
  -PsqlPath '<POSTGRESQL_BIN>\psql.exe'

.\database\scripts\Invoke-SmartCityMigrations.ps1 `
  -Mode Verify `
  -PayaraHome '<PAYARA_HOME>' `
  -PsqlPath '<POSTGRESQL_BIN>\psql.exe'
```

Replace each placeholder with its actual local path. `Migrate` applies the
committed migration sequence to an empty database. `Verify` checks the schema
and migration ledger. `ValidateFiles` only checks the repository migration
files and does not connect to a database. `Baseline` is for reconciliation of
an existing schema, not a fresh database.

## 6. Configure Payara and the JDBC datasource

This Payara/JDBC configuration is required for either database path. The EJB
POM declares PostgreSQL JDBC driver `42.7.11` with `provided` scope, so
the driver is not packaged into the EAR. Obtain that driver separately and
place its JAR in the local domain's library directory, conventionally:

```text
<PAYARA_HOME>\glassfish\domains\domain1\lib
```

Follow your Payara installation's normal procedure to make the driver
available to the domain (including a controlled domain restart when
required). Do not add credentials to project files.

In the Payara Administration Console for the intended domain:

1. Create a JDBC connection pool for PostgreSQL, using the installed
   `org.postgresql.ds.PGSimpleDataSource` driver class and `javax.sql.DataSource`
   resource type.
2. Set its URL to
   `jdbc:postgresql://localhost:5432/smartcity360` (adjust host, port, or
   database name if your local PostgreSQL configuration differs). Set the pool
   properties `user` and `password` to the database login created above. The
   migration and administrator scripts read `URL` or `url`, `user`, and
   `password` from this pool in `domain.xml`.
3. Use the console's pool **Ping** action and confirm it succeeds.
4. Create a JDBC resource with JNDI name **`jdbc/smartcity360`**, associated
   with that pool.

The required JNDI name is defined in
`backend/smartcity360-ejb/src/main/resources/META-INF/persistence.xml`. Keep
the URL, username, and password in local Payara configuration only.

## 7. Provision the initial administrator (Option B only)

This step is only for Option B. Run it only after the fresh database has been
migrated; do not run it after restoring Option A. The script reads the same
configured Payara datasource and requires an **elevated PowerShell
session** running as a local Windows Administrator. It securely prompts for
the initial administrator's email, display name, and password, then requires
the confirmation text `PROVISION`. The administrator password must contain at
least 14 characters. No default credentials are supplied.

```powershell
.\database\scripts\Provision-InitialAdmin.ps1 `
  -PayaraHome '<PAYARA_HOME>' `
  -PsqlPath '<POSTGRESQL_BIN>\psql.exe'
```

Both parameters are supported and override the script's machine-specific
defaults. Replace the placeholders with local paths. The script protects its
temporary SQL file and does not print the password or its hash.

## 8. Build and deploy the backend

From the project root, build the EAR and its reactor dependencies:

```powershell
mvn -pl backend/smartcity360-ear -am clean package
```

The artifact is:

```text
backend\smartcity360-ear\target\smartcity360-ear-1.0-SNAPSHOT.ear
```

In the Payara Administration Console for the configured domain, use
**Applications > Deploy** and select that EAR. The EAR configuration sets the
web context root to `/smartcity360`. Once Payara is running and presenting a
trusted HTTPS certificate, verify the backend health endpoint in a browser:

```text
https://localhost:8181/smartcity360/api/v1/health
```

The endpoint should return the application's health JSON. The REST application
base path is `/api/v1`; the WAR context root comes from the EAR configuration.

## 9. Frontend TLS and startup

Complete the local certificate, Payara TLS, browser-trust, and environment
setup in [frontend/README.md](frontend/README.md) before starting Vite. The
frontend fails closed unless its HTTPS certificate is valid and Payara's
certificate is trusted and matches `localhost`. Do not bypass these checks.

The three required variables are:

- `SMARTCITY360_VITE_CERT_FILE` — Vite's PEM certificate.
- `SMARTCITY360_VITE_KEY_FILE` — its matching PEM private key.
- `SMARTCITY360_PAYARA_CA_FILE` — the PEM CA certificate used to verify Payara.

Keep all certificate and key material outside the repository. From
`frontend\`, install the locked dependencies and start the frontend:

```powershell
npm ci
npm run dev
```

`npm run dev` first runs `npm run verify:tls`; Vite serves at
`https://localhost:5173` and proxies `/smartcity360/api` to
`https://localhost:8181`. Alternatively, from the project root, run the
portable helper:

```powershell
.\frontend\config\Start-SmartCity360-Frontend.ps1
```

The helper expects the three TLS files under
`%LOCALAPPDATA%\SmartCity360\tls` with the filenames documented in
`frontend/README.md`.

## 10. Demo administrator credentials

For the included demo backup, the supplied login details are:

- Email: `admin@gmail.com`
- Password placeholder: `Rivi@admin/360#2006`

These credentials work only if the restored backup contains that administrator
account and its stored password hash matches the supplied password. This has
not been verified. If either condition is not met, use an appropriate
administrator account; do not run Option B's provisioning script on top of
the restored database.

## 11. First login and application checklist

Open `https://localhost:5173/login` and sign in with the administrator
credentials for the selected database path: the demo credentials in Section
10 for Option A (if the account and password hash match), or the credentials
entered during provisioning for Option B. A successful login should take
you to the authenticated `/app` area and show the signed-in user. Use the
administrator navigation to inspect:

- Dashboard
- Smart Zones (navigation label: Zones)
- Citizen Complaints (navigation label: Complaints)
- Streetlights
- Energy Streams
- Policy Interpreter (navigation label: Policies)

The dashboard health response and authenticated user are live API data. Use
the browser's normal trusted HTTPS connection; do not use an IP address or
different hostname in place of `localhost`.

## 12. Academic demonstration limitations

A clean migrated database contains schema and role definitions, not
preloaded operational records. Empty lists or dashboards are therefore
possible and do not imply that the corresponding source-code functionality is
missing. Optional academic Energy Streams fixtures and their configuration
are described in
[`database/scripts/README-EnergyStreamAcademicDemo.md`](database/scripts/README-EnergyStreamAcademicDemo.md).
That demo requires separately provisioned Payara cryptographic aliases and
explicit operator preparation. Do not place key material or sample passwords
in the repository.

## 13. Troubleshooting

- **Wrong Java version:** Check `java -version` and `mvn -version`; select a
  JDK 17 installation before building.
- **Maven dependency failures:** Confirm Maven can reach its configured
  repositories and that the selected Maven/JDK versions are compatible.
- **Missing JDBC driver:** Confirm the PostgreSQL JDBC 42.7.11 JAR is
  available to the Payara domain and that the domain was restarted if needed.
- **Datasource name or ping failure:** Confirm `jdbc/smartcity360` exists,
  points to the intended pool, and the pool's PostgreSQL URL and credentials
  are correct.
- **Migration failure:** Confirm the database already exists, the datasource
  points to it, and its login can create schema objects. Use `Migrate` only on
  a clean schema; do not use `Baseline` as a substitute for fresh
  initialization. For Option A, do not run migrations against the restored
  backup.
- **Missing initial administrator:** Confirm migrations completed (including
  the role seed), the script uses the same Payara domain/database, and
  PowerShell is elevated.
- **TLS, SAN, or browser trust warning:** Recheck the certificate dates,
  `DNS:localhost` SAN, server-auth usage, certificate/key pair, Payara
  listener certificate, and local CA trust. Follow
  [frontend/README.md](frontend/README.md); never bypass certificate checks.
- **Vite proxy connection failure:** Confirm Payara is running at
  `https://localhost:8181`, the health endpoint responds, and
  `SMARTCITY360_PAYARA_CA_FILE` contains the correct CA certificate.
- **Authentication or CSRF error:** Use the exact `https://localhost:5173`
  origin, do not switch between `localhost` and an IP address, and ensure the
  browser accepts the local development certificate.

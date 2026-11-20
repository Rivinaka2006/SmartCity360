# SmartCity360 frontend

This is a standalone React/TypeScript development workspace. It uses the
Payara session cookie and API under `/smartcity360`; it is not packaged into
the EAR. The frontend phase contains the secure application foundation and
module interfaces; operational records are not preloaded into a clean
database.

For the complete backend, database, build, and deployment sequence, see the
[root installation guide](../README.md).

## Requirements and startup

- Node.js 24 or newer and npm.
- A running Payara development domain at `https://localhost:8181`.
- Locally provisioned Vite and Payara TLS material, described below.

Install the locked dependencies from this directory with `npm ci`. Set the
three TLS environment variables below in the current PowerShell process, then
run:

```powershell
npm run verify:tls
npm run dev
```

`npm run dev` automatically runs the TLS verification pre-step as well. Vite
listens at `https://localhost:5173` and proxies
`/smartcity360/api` to `https://localhost:8181`.

The supported PowerShell launcher can also be run from the project root:

```powershell
.\frontend\config\Start-SmartCity360-Frontend.ps1
```

It resolves the frontend directory relative to itself and expects TLS files
under `%LOCALAPPDATA%\SmartCity360\tls` using these names:

- `vite-localhost.pem`
- `vite-localhost-key.pem`
- `payara-ca.pem`

## Local Windows TLS setup

These are local-development certificates only, not production certificate
guidance. Do not disable browser or Node.js certificate checks, hostname
verification, or `rejectUnauthorized`. The application requires current
certificates with a `localhost` hostname and explicit Payara trust.

### 1. Install certificate utilities separately

Install [mkcert](https://github.com/FiloSottile/mkcert#installation) separately
using its documented Windows installation options. It is not included in this
repository. Install OpenSSL separately as well; it is used only to package the
Payara certificate and key as PKCS#12. See the [OpenSSL PKCS#12
manual](https://docs.openssl.org/3.5/man1/openssl-pkcs12/) for its supported
options. The JDK 17 prerequisite supplies `keytool`; set `JAVA_HOME` to that
JDK before using the commands below. See the [JDK 17 keytool
manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
for keystore import options.

`mkcert -install` creates a local development CA and adds its public
certificate to supported local trust stores. On Windows this changes the
trust store used by the installed mkcert build; review and approve that
persistent trust change for the development computer.
Chromium-based browsers can use the Windows trust store. In Firefox, if the
certificate is not trusted after installation, import `rootCA.pem` under
**Settings > Privacy & Security > Certificates > View Certificates >
Authorities**, and trust it to identify websites. Restart the browser after
changing its trust configuration.

### 2. Create local certificates outside the repository

Run in PowerShell. These commands create files under the current user's local
application data directory, not in the repository:

```powershell
$tlsDir = Join-Path $env:LOCALAPPDATA 'SmartCity360\tls'
New-Item -ItemType Directory -Force -Path $tlsDir | Out-Null

mkcert -install
$caRoot = (mkcert -CAROOT).Trim()

mkcert -cert-file (Join-Path $tlsDir 'vite-localhost.pem') `
  -key-file (Join-Path $tlsDir 'vite-localhost-key.pem') `
  localhost

mkcert -cert-file (Join-Path $tlsDir 'payara-localhost.pem') `
  -key-file (Join-Path $tlsDir 'payara-localhost-key.pem') `
  localhost 127.0.0.1 ::1

Copy-Item -LiteralPath (Join-Path $caRoot 'rootCA.pem') `
  -Destination (Join-Path $tlsDir 'payara-ca.pem')
```

The certificates are separate server certificates signed by this machine's
local development CA. They include `localhost` as a DNS name; the Payara
certificate also includes the loopback IP SANs. The Vite certificate must
have the `serverAuth` extended key usage, be within its validity dates, and
match its private key; `npm run verify:tls` checks those Vite requirements.
Inspect the Payara certificate too and confirm its SAN, server-auth extended
key usage, issuer, and validity dates before installing it:

```powershell
openssl x509 -in (Join-Path $tlsDir 'payara-localhost.pem') -noout -text
```

The mkcert root certificate is `rootCA.pem`; `rootCA-key.pem` is the CA's
private signing key. Never copy, commit, or share `rootCA-key.pem`, either
server private key, or any PKCS#12 file containing a private key.

### 3. Configure the Payara HTTPS listener

Payara must present a certificate trusted by the local CA and valid for
`localhost`. Do not assume the certificate installed with Payara is suitable;
the existing certificate may lack the required SAN.

The project procedure assumes a local `domain1` whose keystore is
`<PAYARA_HOME>\glassfish\domains\domain1\config\keystore.p12`. Confirm the
actual keystore path, type, listener, and `server-config` in that domain's
`domain.xml` before changing anything. Back up `domain.xml` and the keystore
to an access-controlled location. Do not overwrite or delete the existing
`s1as` entry.

Choose a new, unique alias for the development certificate and replace the
placeholders below. OpenSSL prompts for the PKCS#12 export password; do not
put passwords in command arguments:

```powershell
$payaraAlias = '<CHOOSE_A_NEW_UNIQUE_ALIAS>'
$payaraCert = Join-Path $tlsDir 'payara-localhost.pem'
$payaraKey = Join-Path $tlsDir 'payara-localhost-key.pem'
$payaraP12 = Join-Path $tlsDir 'payara-localhost.p12'
$payaraKeystore = '<PAYARA_HOME>\glassfish\domains\domain1\config\keystore.p12'
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
  throw 'Set JAVA_HOME to the JDK 17 installation before using keytool.'
}
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) {
  throw "keytool was not found: $keytool"
}

openssl pkcs12 -export -in $payaraCert -inkey $payaraKey `
  -certfile (Join-Path $caRoot 'rootCA.pem') `
  -name $payaraAlias -out $payaraP12

& $keytool -list -keystore $payaraP12 -storetype PKCS12
& $keytool -importkeystore `
  -srckeystore $payaraP12 -srcstoretype PKCS12 -srcalias $payaraAlias `
  -destkeystore $payaraKeystore -deststoretype PKCS12 -destalias $payaraAlias
```

Replace `<PAYARA_HOME>` with the actual installation directory, and replace
`<CHOOSE_A_NEW_UNIQUE_ALIAS>` with an alias not already in the domain
keystore. Confirm the alias shown by `keytool -list` matches. `keytool`
prompts for the source and destination keystore passwords; do not put them in
the command line, shell history, or this repository. Stop if the configured
keystore differs from this procedure or the alias already exists; use the
Payara administrator's documented process for that domain instead.

Using the Payara Administration Console, configure the local
`server-config` `http-listener-2` SSL certificate nickname to the imported
alias. The corresponding configuration property is
`server-config.network-config.protocols.protocol.http-listener-2.ssl.cert-nickname`.
Preserve the previous configuration and use the domain administrator's
approved restart procedure. Do not change other listeners or remove the
existing alias. These steps are for a local development domain only.

### 4. Set the frontend variables and verify

Set all three variables in the same PowerShell session that will run npm. Use
absolute local paths:

```powershell
$env:SMARTCITY360_VITE_CERT_FILE = Join-Path $tlsDir 'vite-localhost.pem'
$env:SMARTCITY360_VITE_KEY_FILE = Join-Path $tlsDir 'vite-localhost-key.pem'
$env:SMARTCITY360_PAYARA_CA_FILE = Join-Path $tlsDir 'payara-ca.pem'

npm run verify:tls
```

The verification uses the configured CA, connects to Payara at
`https://localhost:8181`, and checks trust, hostname, and validity. It also
checks the Vite certificate's `DNS:localhost` SAN, `serverAuth` usage,
validity, and matching private key. The browser independently verifies the
Vite certificate chain; trust the mkcert CA in the browser's local trust store
as described above. Do not rely on a browser warning bypass.

`npm run verify:tls` reads process environment variables. Do not rely only on
`frontend/.env.local` for this probe; Vite's environment-file loading does
not apply to the standalone verification process.

The Payara public CA certificate is configured explicitly for the frontend
proxy. Keep both private keys and all certificate files outside Git. The
mkcert CA private key grants the ability to issue certificates trusted by
this development computer, so protect it and do not distribute it.

> **Development-only warning:** mkcert, a locally trusted CA, and the
> development listener configuration are for a controlled local development
> computer only. Do not use these certificates, keys, or trust procedures as
> production deployment guidance.

## Application behavior and security boundaries

- Browser API requests use relative `/smartcity360/api/v1` paths and
  same-origin credentials. Vite proxies without path rewriting or changing
  the browser's Host/Origin headers.
- `JSESSIONID` remains browser-managed. The frontend does not read or persist
  cookies, passwords, or session credentials.
- CSRF tokens stay in memory and are sent in `X-CSRF-Token` for login/logout.
- Navigation visibility is derived from backend roles for usability. Backend
  authorization remains authoritative.
- The dashboard displays the authenticated user and live health response; it
  does not fabricate operational metrics.

## Verification commands

Run from this directory after dependencies and TLS are configured:

```powershell
npm run typecheck
npm run lint
npm test
npm run build
npm run verify:tls
```

`npm run verify:tls` is a live check against Payara and requires the configured
TLS material and a running Payara HTTPS listener. Isolated frontend tests do
not establish live Payara connectivity.

import { createPrivateKey, createPublicKey, X509Certificate } from 'node:crypto'
import { readFileSync, statSync } from 'node:fs'
import { Agent } from 'node:https'
import { isAbsolute } from 'node:path'
import { connect, createSecureContext } from 'node:tls'

const CERTIFICATE_LIMIT_BYTES = 1024 * 1024
const SERVER_AUTH_OID = '1.3.6.1.5.5.7.3.1'
const REQUIRED_VARIABLES = [
  'SMARTCITY360_VITE_CERT_FILE',
  'SMARTCITY360_VITE_KEY_FILE',
  'SMARTCITY360_PAYARA_CA_FILE',
]

/** @param {Record<string, string | undefined>} environment @param {string} name */
function readConfiguredFile(environment, name) {
  const path = environment[name]
  if (!path) throw new Error(`Missing required TLS configuration: ${name}`)
  if (!isAbsolute(path)) throw new Error(`${name} must contain an absolute path`)
  let details
  try {
    details = statSync(path)
  } catch {
    throw new Error(`${name} does not reference a readable file`)
  }
  if (!details.isFile() || details.size === 0 || details.size > CERTIFICATE_LIMIT_BYTES) {
    throw new Error(`${name} must reference a non-empty regular file no larger than 1 MiB`)
  }
  try {
    return readFileSync(path)
  } catch {
    throw new Error(`${name} could not be read`)
  }
}

/** @param {Buffer} pem @param {string} label */
function parseCertificate(pem, label) {
  try {
    return new X509Certificate(pem)
  } catch {
    throw new Error(`${label} certificate is invalid`)
  }
}

/** @param {X509Certificate} certificate @param {Date} now */
function validateFrontendCertificate(certificate, now) {
  const validFrom = new Date(certificate.validFrom)
  const validTo = new Date(certificate.validTo)
  if (Number.isNaN(validFrom.valueOf()) || Number.isNaN(validTo.valueOf())) {
    throw new Error('Frontend certificate validity period is invalid')
  }
  if (now < validFrom) throw new Error('Frontend certificate is not yet valid')
  if (now > validTo) throw new Error('Frontend certificate has expired')
  if (!certificate.subjectAltName || !certificate.subjectAltName.includes('DNS:localhost') || !certificate.checkHost('localhost')) {
    throw new Error('Frontend certificate requires a localhost subject alternative name')
  }
  if (!certificate.keyUsage?.includes(SERVER_AUTH_OID)) {
    throw new Error('Frontend certificate requires the serverAuth extended key usage')
  }
}

/** @param {Buffer} certificatePem @param {Buffer} privateKeyPem @param {X509Certificate} certificate */
function validatePrivateKey(certificatePem, privateKeyPem, certificate) {
  try {
    createSecureContext({ cert: certificatePem, key: privateKeyPem })
    const certificatePublicKey = certificate.publicKey.export({ type: 'spki', format: 'der' })
    const privateKeyPublicKey = createPublicKey(createPrivateKey(privateKeyPem)).export({ type: 'spki', format: 'der' })
    if (!certificatePublicKey.equals(privateKeyPublicKey)) throw new Error('mismatch')
  } catch {
    throw new Error('Frontend private key is invalid or does not match its certificate')
  }
}

/**
 * @param {Record<string, string | undefined>} environment
 * @param {{ now?: Date }} [options]
 */
export function loadTlsConfiguration(environment, options = {}) {
  for (const name of REQUIRED_VARIABLES) {
    if (!environment[name]) throw new Error(`Missing required TLS configuration: ${name}`)
  }
  const viteCertificate = readConfiguredFile(environment, REQUIRED_VARIABLES[0])
  const vitePrivateKey = readConfiguredFile(environment, REQUIRED_VARIABLES[1])
  const payaraCa = readConfiguredFile(environment, REQUIRED_VARIABLES[2])
  const frontendCertificate = parseCertificate(viteCertificate, 'Frontend')
  validateFrontendCertificate(frontendCertificate, options.now ?? new Date())
  validatePrivateKey(viteCertificate, vitePrivateKey, frontendCertificate)
  parseCertificate(payaraCa, 'Payara trust')

  const payaraAgent = new Agent({
    ca: payaraCa,
    keepAlive: true,
    rejectUnauthorized: true,
  })

  return { viteCertificate, vitePrivateKey, payaraCa, payaraAgent }
}

/**
 * @typedef {{ subject: string, issuer: string, validFrom: string, validTo: string, fingerprint256: string }} VerifiedPeer
 * @param {{ host?: string, port?: number, servername?: string, ca: Buffer, timeoutMs?: number }} options
 * @returns {Promise<VerifiedPeer>}
 */
export function verifyPayaraTls(options) {
  const host = options.host ?? 'localhost'
  const port = options.port ?? 8181
  const servername = options.servername ?? 'localhost'
  const timeoutMs = options.timeoutMs ?? 5000

  return new Promise((resolve, reject) => {
    let settled = false
    /** @param {Error | undefined} error @param {VerifiedPeer | undefined} peer */
    const finish = (error, peer) => {
      if (settled) return
      settled = true
      socket.end()
      if (error) reject(error)
      else if (peer) resolve(peer)
      else reject(new Error('Payara TLS verification produced no peer details'))
    }
    const socket = connect({
      host,
      port,
      servername,
      ca: options.ca,
      rejectUnauthorized: true,
    }, () => {
      try {
        if (!socket.authorized) throw new Error('Payara TLS certificate was not authorized')
        const peer = socket.getPeerX509Certificate()
        if (!peer) throw new Error('Payara did not present a certificate')
        if (!peer.checkHost(servername)) throw new Error(`Payara certificate does not match hostname ${servername}`)
        const now = Date.now()
        if (now < Date.parse(peer.validFrom) || now > Date.parse(peer.validTo)) {
          throw new Error('Payara certificate is outside its validity period')
        }
        finish(undefined, {
          subject: peer.subject,
          issuer: peer.issuer,
          validFrom: peer.validFrom,
          validTo: peer.validTo,
          fingerprint256: peer.fingerprint256,
        })
      } catch (error) {
        finish(error instanceof Error ? error : new Error('Payara TLS verification failed'), undefined)
      }
    })
    socket.setTimeout(timeoutMs, () => finish(new Error('Payara TLS verification timed out'), undefined))
    socket.once('error', (error) => finish(error, undefined))
  })
}

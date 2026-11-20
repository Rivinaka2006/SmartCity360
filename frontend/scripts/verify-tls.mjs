import { loadTlsConfiguration, verifyPayaraTls } from '../config/tls.mjs'

try {
  const configuration = loadTlsConfiguration(process.env)
  const peer = await verifyPayaraTls({ ca: configuration.payaraCa })
  console.log(`Verified Payara TLS peer: ${peer.subject}`)
  console.log(`Validity: ${peer.validFrom} to ${peer.validTo}`)
  console.log(`SHA-256 fingerprint: ${peer.fingerprint256}`)
} catch (error) {
  const message = error instanceof Error ? error.message : 'Unknown TLS configuration error'
  console.error(`TLS verification failed: ${message}`)
  process.exitCode = 1
}

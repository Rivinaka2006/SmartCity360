import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createServer } from 'node:tls'
import forge from 'node-forge'
import { afterEach, describe, expect, it } from 'vitest'
import { loadTlsConfiguration, verifyPayaraTls } from './tls.mjs'

type CertificateOptions = {
  host?: string
  notBefore?: Date
  notAfter?: Date
  includeServerAuth?: boolean
}

const temporaryDirectories: string[] = []

function createCertificate(options: CertificateOptions = {}) {
  const keys = forge.pki.rsa.generateKeyPair(2048)
  const certificate = forge.pki.createCertificate()
  certificate.publicKey = keys.publicKey
  certificate.serialNumber = Math.floor(Math.random() * 1_000_000).toString(16)
  certificate.validity.notBefore = options.notBefore ?? new Date(Date.now() - 60_000)
  certificate.validity.notAfter = options.notAfter ?? new Date(Date.now() + 86_400_000)
  const attributes = [{ name: 'commonName', value: options.host ?? 'localhost' }]
  certificate.setSubject(attributes)
  certificate.setIssuer(attributes)
  const extensions: Parameters<typeof certificate.setExtensions>[0] = []
  if (options.host) {
    extensions.push({
      name: 'subjectAltName',
      altNames: [
        { type: 2, value: options.host },
        { type: 7, ip: '127.0.0.1' },
      ],
    })
  }
  if (options.includeServerAuth !== false) {
    extensions.push({ name: 'extKeyUsage', serverAuth: true })
  }
  certificate.setExtensions(extensions)
  certificate.sign(keys.privateKey, forge.md.sha256.create())
  return {
    certificate: forge.pki.certificateToPem(certificate),
    privateKey: forge.pki.privateKeyToPem(keys.privateKey),
  }
}

function writeTlsFiles(options: CertificateOptions = { host: 'localhost' }) {
  const directory = mkdtempSync(join(tmpdir(), 'smartcity360-tls-'))
  temporaryDirectories.push(directory)
  const pair = createCertificate(options)
  const certificatePath = join(directory, 'frontend.pem')
  const keyPath = join(directory, 'frontend-key.pem')
  const payaraCaPath = join(directory, 'payara.pem')
  writeFileSync(certificatePath, pair.certificate)
  writeFileSync(keyPath, pair.privateKey)
  writeFileSync(payaraCaPath, pair.certificate)
  return { directory, pair, certificatePath, keyPath, payaraCaPath }
}

function environment(files: ReturnType<typeof writeTlsFiles>) {
  return {
    SMARTCITY360_VITE_CERT_FILE: files.certificatePath,
    SMARTCITY360_VITE_KEY_FILE: files.keyPath,
    SMARTCITY360_PAYARA_CA_FILE: files.payaraCaPath,
  }
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true })
  }
})

describe('TLS configuration', () => {
  it('rejects missing certificate variables without exposing secrets', () => {
    expect(() => loadTlsConfiguration({})).toThrow(/SMARTCITY360_VITE_CERT_FILE/)
  })

  it('loads matching, current certificate material with a localhost SAN', () => {
    const files = writeTlsFiles()
    const result = loadTlsConfiguration(environment(files))

    expect(result.viteCertificate).toBeInstanceOf(Buffer)
    expect(result.vitePrivateKey).toBeInstanceOf(Buffer)
    expect(result.payaraCa).toBeInstanceOf(Buffer)
    expect(result.payaraAgent.options.rejectUnauthorized).toBe(true)
  })

  it('rejects a configured TLS file that does not exist', () => {
    const files = writeTlsFiles()
    rmSync(files.keyPath)

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/SMARTCITY360_VITE_KEY_FILE.*readable file/i)
  })

  it('rejects malformed certificate material', () => {
    const files = writeTlsFiles()
    writeFileSync(files.certificatePath, 'not a certificate')

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/certificate/i)
  })

  it('rejects a malformed private key', () => {
    const files = writeTlsFiles()
    writeFileSync(files.keyPath, 'not a private key')

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/private key/i)
  })

  it('rejects a private key that does not match the frontend certificate', () => {
    const files = writeTlsFiles()
    writeFileSync(files.keyPath, createCertificate({ host: 'localhost' }).privateKey)

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/private key/i)
  })

  it('rejects certificates without a localhost subject alternative name', () => {
    const files = writeTlsFiles({})

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/localhost.*alternative name/i)
  })

  it('rejects expired frontend certificates', () => {
    const files = writeTlsFiles({
      host: 'localhost',
      notBefore: new Date(Date.now() - 172_800_000),
      notAfter: new Date(Date.now() - 86_400_000),
    })

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/expired/i)
  })

  it('rejects frontend certificates that are not yet valid', () => {
    const files = writeTlsFiles({
      host: 'localhost',
      notBefore: new Date(Date.now() + 86_400_000),
      notAfter: new Date(Date.now() + 172_800_000),
    })

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/not yet valid/i)
  })

  it('rejects frontend certificates without server authentication usage', () => {
    const files = writeTlsFiles({ host: 'localhost', includeServerAuth: false })

    expect(() => loadTlsConfiguration(environment(files))).toThrow(/serverAuth/i)
  })

  it('rejects a trusted peer whose certificate does not match localhost', async () => {
    const peer = createCertificate({ host: 'other.test' })
    const server = createServer({ cert: peer.certificate, key: peer.privateKey })
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
    const address = server.address()
    if (!address || typeof address === 'string') throw new Error('TLS test server did not bind')

    try {
      await expect(verifyPayaraTls({
        host: '127.0.0.1',
        port: address.port,
        servername: 'localhost',
        ca: Buffer.from(peer.certificate),
      })).rejects.toThrow(/alternative name|hostname|localhost/i)
    } finally {
      await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
    }
  })
})

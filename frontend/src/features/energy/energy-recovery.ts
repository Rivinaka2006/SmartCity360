const maxDemoPayloadBytes = 1_024
const maxCompressedDemoBytes = 1_536

export async function recoverDemoPayload(payloadBase64: string) {
  if (payloadBase64.length > 2_048
    || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(payloadBase64)) {
    throw new Error('The decrypted demo payload is not valid bounded Base64 data.')
  }
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('This browser does not support bounded GZIP recovery.')
  }

  const binary = atob(payloadBase64)
  if (binary.length > maxCompressedDemoBytes) {
    throw new Error('The compressed demo payload exceeds the size limit.')
  }
  const compressedBuffer = new ArrayBuffer(binary.length)
  const compressed = new Uint8Array(compressedBuffer)
  for (let index = 0; index < binary.length; index += 1) {
    compressed[index] = binary.charCodeAt(index)
  }
  const decompressor = new DecompressionStream('gzip')
  const reader = decompressor.readable.getReader()
  const writer = decompressor.writable.getWriter()
  const chunks: Uint8Array[] = []
  let totalBytes = 0

  try {
    const readPayload = async () => {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        totalBytes += value.byteLength
        if (totalBytes > maxDemoPayloadBytes) {
          void writer.abort().catch(() => undefined)
          throw new Error('The recovered demo payload exceeds the size limit.')
        }
        chunks.push(value)
      }
    }
    const writePayload = async () => {
      await writer.write(compressed)
      await writer.close()
    }
    await Promise.all([readPayload(), writePayload()])
  } finally {
    reader.releaseLock()
    writer.releaseLock()
  }

  const recovered = new Uint8Array(totalBytes)
  let offset = 0
  for (const chunk of chunks) {
    recovered.set(chunk, offset)
    offset += chunk.byteLength
  }
  return new TextDecoder('utf-8', { fatal: true }).decode(recovered)
}

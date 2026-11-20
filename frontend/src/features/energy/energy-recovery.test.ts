import { describe, expect, it } from 'vitest'
import { recoverDemoPayload } from './energy-recovery'

const recoveredReading = '{"meterCode":"ACADEMIC-VIVA-METER","consumptionKwh":1.25,"intervalMinutes":15}'
const compressedReading = 'H4sIAAAAAAAACqtWyk0tSS1yzk9JVbJScnR2dHH19XTWDfMMc9T1dQ1xDVLSUUrOzysuzS0oyczP8y7PULIy1DMy1VHKzCtJLSpLzPHNzCstSS1WsjI0rQUAe4kHqU4AAAA='
const oversizedPayload = 'H4sIAAAAAAAACquoGAWjYBSMglEwCkbBSAMAzzRSbwAIAAA='

describe('recoverDemoPayload', () => {
  it('decompresses the bounded verified demo payload', async () => {
    await expect(recoverDemoPayload(compressedReading)).resolves.toBe(recoveredReading)
  })

  it('rejects output exceeding the decompression limit', async () => {
    await expect(recoverDemoPayload(oversizedPayload)).rejects.toThrow('exceeds the size limit')
  })

  it('rejects malformed Base64 before decompression', async () => {
    await expect(recoverDemoPayload('not-base64!')).rejects.toThrow('valid bounded Base64')
  })
})

package com.smartcity360.ejb.energy.stream;

import java.io.*;
import java.util.zip.*;

public final class GzipPayloadCodec {
    private GzipPayloadCodec() {
    }

    public static byte[] compress(byte[] input, int max) {
        if (input == null || input.length > max) throw new EnergyStreamException("PAYLOAD_SIZE_INVALID");
        try {
            var out = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(out)) {
                gzip.write(input);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new EnergyStreamException("COMPRESSION_FAILED", e);
        }
    }

    public static byte[] decompress(byte[] input, int max) {
        if (input == null || input.length > max) throw new EnergyStreamException("COMPRESSED_SIZE_LIMIT");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(input)); var out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096];
            int n;
            while ((n = gzip.read(b)) >= 0) {
                if (out.size() + n > max) throw new EnergyStreamException("DECOMPRESSED_SIZE_LIMIT");
                out.write(b, 0, n);
            }
            return out.toByteArray();
        } catch (EnergyStreamException e) {
            throw e;
        } catch (IOException e) {
            throw new EnergyStreamException("COMPRESSED_PAYLOAD_INVALID", e);
        }
    }
}

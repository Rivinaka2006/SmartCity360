package com.smartcity360.ejb.energy.stream;

import javax.crypto.SecretKey;

public interface KeyProvider {
    MeterKey activeMeterHmacKey(String keyId, int version);

    SecretKey resolveMeterHmacKey(String keyId, int version);

    EncryptionKey currentEncryptionKey();

    SecretKey resolveEncryptionKey(String keyId);
}

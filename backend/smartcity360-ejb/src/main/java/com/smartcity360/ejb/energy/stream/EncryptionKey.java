package com.smartcity360.ejb.energy.stream;

import javax.crypto.SecretKey;

public record EncryptionKey(String id, SecretKey key) {
}

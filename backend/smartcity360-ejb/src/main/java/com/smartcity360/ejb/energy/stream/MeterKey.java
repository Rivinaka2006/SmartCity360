package com.smartcity360.ejb.energy.stream;

import javax.crypto.SecretKey;

public record MeterKey(String id, int version, SecretKey key) {
}

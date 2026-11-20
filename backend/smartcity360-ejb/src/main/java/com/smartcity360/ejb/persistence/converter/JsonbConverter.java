package com.smartcity360.ejb.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

@Converter
public class JsonbConverter implements AttributeConverter<String, PGobject> {
    @Override
    public PGobject convertToDatabaseColumn(String value) {
        if (value == null) return null;
        PGobject object = new PGobject();
        object.setType("jsonb");
        try {
            object.setValue(value);
        } catch (SQLException exception) {
            throw new IllegalArgumentException("Invalid JSONB value", exception);
        }
        return object;
    }

    @Override
    public String convertToEntityAttribute(PGobject value) {
        return value == null ? null : value.getValue();
    }
}

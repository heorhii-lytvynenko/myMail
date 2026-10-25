package com.mymail.integration.google.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter
@RequiredArgsConstructor
public class EncryptedTokenConverter implements AttributeConverter<String, String> {
    private final CredentialCipher cipher;

    @Override
    public String convertToDatabaseColumn(String value) {
        return cipher.encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String value) {
        return cipher.decrypt(value);
    }
}

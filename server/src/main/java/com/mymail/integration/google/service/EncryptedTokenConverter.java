package com.mymail.integration.google.service;

import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@Converter
@RequiredArgsConstructor
public class EncryptedTokenConverter implements AttributeConverter<String, String> {
    private final CredentialCipher cipher;
    public String convertToDatabaseColumn(String value) { return cipher.encrypt(value); }
    public String convertToEntityAttribute(String value) { return cipher.decrypt(value); }
}

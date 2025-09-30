package com.audience.app.converter;

import jakarta.persistence.AttributeConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.security.Key;

@Component
public class TokenEncryptor implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private final Key secretKeySpec;

    public TokenEncryptor(@Value("${audiance.security.encryptor-key}") String secretKey) {
        byte[] keyBytes = new byte[16];
        byte[] providedKeyBytes = secretKey.getBytes();
        System.arraycopy(providedKeyBytes, 0, keyBytes, 0, Math.min(providedKeyBytes.length, keyBytes.length));
        this.secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, this.secretKeySpec);
            return Base64.getEncoder().encodeToString(c.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting attribute", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, this.secretKeySpec);
            return new String(c.doFinal(Base64.getDecoder().decode(dbData)));
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting attribute", e);
        }
    }
}
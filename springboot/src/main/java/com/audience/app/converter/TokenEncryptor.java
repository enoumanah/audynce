package com.audience.app.converter;

import jakarta.persistence.AttributeConverter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class TokenEncryptor implements AttributeConverter<String, String> {

    public static final String ALGORITHM = "AES";
    public static final byte[] KEY = "your-super-secret-key-12345678".getBytes();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM));
            return Base64.getEncoder().encodeToString(c.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, ALGORITHM));
            return new String(c.doFinal(Base64.getDecoder().decode(dbData)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

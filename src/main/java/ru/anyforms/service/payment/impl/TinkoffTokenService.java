package ru.anyforms.service.payment.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Service
@Slf4j
class TinkoffTokenService {

    @Value("${payment.tinkoff.password}")
    private String password;

    public String sign(Map<String, String> rootParams) {
        TreeMap<String, String> params = new TreeMap<>(rootParams);
        params.put("Password", password);
        String concatenated = String.join("", params.values());
        return sha256Hex(concatenated);
    }

    public boolean verify(JsonNode notification) {
        JsonNode tokenNode = notification.get("Token");
        if (tokenNode == null || tokenNode.asText().isBlank()) {
            return false;
        }
        TreeMap<String, String> params = new TreeMap<>();
        notification.fields().forEachRemaining(entry -> {
            if (!"Token".equals(entry.getKey())
                    && entry.getValue().isValueNode()
                    && !entry.getValue().isNull()) {
                params.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return sign(params).equalsIgnoreCase(tokenNode.asText());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}

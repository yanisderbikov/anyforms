package ru.anyforms.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class DataFormattingService {

    /**
     * Форматирует дату из timestamp в формат dd/MM/yyyy
     */
    public String formatDate(String dateValue) {
        if (dateValue == null || dateValue.isEmpty()) {
            return "";
        }
        try {
            // Try to parse as timestamp (Unix timestamp in seconds or milliseconds)
            long timestamp;
            if (dateValue.length() > 10) {
                timestamp = Long.parseLong(dateValue) / 1000; // Assume milliseconds
            } else {
                timestamp = Long.parseLong(dateValue);
            }
            LocalDate date = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            // If parsing fails, return as is
            return dateValue;
        }
    }

    /**
     * Нормализует номер телефона: убирает символы "+" и дефисы
     */
    public String normalizePhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        // Убираем все символы "+" и дефисы
        return phone.replace("+", "").replace("-", "");
    }
}


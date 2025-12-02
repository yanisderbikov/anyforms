package ru.anyforms.integration.impl;

import ru.anyforms.integration.GoogleSheetsGateway;
import ru.anyforms.util.sheets.GoogleSheetsColumnIndex;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Component
class GoogleSheetsApiGateway implements GoogleSheetsGateway {
    private static final String APPLICATION_NAME = "AmoCRM Webhook Service";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    @Value("${google.sheets.spreadsheet.id:}")
    private String spreadsheetId;

    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    @Value("${google.sheets.credentials.json}")
    private String credentialsJson;

    private GoogleCredentials getCredentials() throws IOException {
        InputStream credentialsStream = null;
        
        // First, try to load from environment variable (JSON content)
        if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
            credentialsStream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }
        
        if (credentialsStream == null) {
            throw new IOException("Service account credentials not found. " +
                    "Please set GOOGLE_CREDENTIALS_JSON environment variable with full JSON content.");
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(SCOPES);
        credentialsStream.close();
        return credentials;
    }

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = getCredentials();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Находит следующую свободную строку в таблице
     * Игнорирует столбец K при проверке - если заполнен только K, строка считается свободной
     */
    private int findNextEmptyRow(Sheets service, String sheetName) throws IOException {
        // Читаем данные из таблицы (все столбцы до Z)
        String range = sheetName + "!A:Z";
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        
        List<List<Object>> values = response.getValues();
        
        if (values == null || values.isEmpty()) {
            return 1; // Таблица пустая, начинаем с первой строки
        }
        
        // Ищем первую пустую строку (игнорируя столбец K)
        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            
            if (row == null || row.isEmpty()) {
                return i + 1; // Строка полностью пустая
            }
            
            // Проверяем, пуста ли строка, игнорируя столбец K
            boolean isEmpty = true;
            for (int j = 0; j < row.size(); j++) {
                // Пропускаем столбец K
                if (j == GoogleSheetsColumnIndex.COLUMN_K_INDEX) {
                    continue;
                }
                
                Object cell = row.get(j);
                if (cell != null && !cell.toString().trim().isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            
            if (isEmpty) {
                return i + 1; // Строка свободна (кроме столбца K)
            }
        }
        
        // Если все строки заняты, возвращаем следующую после последней
        return values.size() + 1;
    }

    @Override
    public void appendRow(List<Object> rowData) {
        appendRow(rowData, "Под заказ");
    }

    @Override
    public void appendRow(List<Object> rowData, String sheetName) {
        try {
            Sheets service = getSheetsService();
            
            // Находим следующую свободную строку
            int nextRow = findNextEmptyRow(service, sheetName);
            
            // Формируем диапазон для вставки в конкретную строку
            String range = sheetName + "!A" + nextRow + ":Z" + nextRow;
            
            ValueRange body = new ValueRange()
                    .setValues(Collections.singletonList(rowData));
            
            // Вставляем данные в найденную строку
            service.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to append row to Google Sheets", e);
        }
    }

    @Override
    public List<List<Object>> readAllRows(String sheetName) {
        try {
            Sheets service = getSheetsService();
            String range = sheetName + "!A:Z";
            ValueRange response = service.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            
            return response.getValues() != null ? response.getValues() : Collections.emptyList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read rows from Google Sheets", e);
        }
    }

    @Override
    public List<List<Object>> readAllRows() {
        return readAllRows(sheetName);
    }

    @Override
    public String getCellValue(List<Object> row, int columnIndex) {
        if (row == null || columnIndex < 0 || columnIndex >= row.size()) {
            return "";
        }
        Object value = row.get(columnIndex);
        return value != null ? value.toString().trim() : "";
    }

    @Override
    public boolean isRowFilled(List<Object> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        
        // Проверяем, есть ли хотя бы одна заполненная ячейка (кроме K)
        for (int i = 0; i < row.size(); i++) {
            // Пропускаем столбец K
            if (i == GoogleSheetsColumnIndex.COLUMN_K_INDEX) {
                continue;
            }
            
            Object cell = row.get(i);
            if (cell != null && !cell.toString().trim().isEmpty()) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public List<List<Object>> readLastFilledRows(String sheetName, int count) {
        try {
            Sheets service = getSheetsService();
            String range = sheetName + "!A:Z";
            ValueRange response = service.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            
            List<List<Object>> allRows = response.getValues();
            if (allRows == null || allRows.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Фильтруем заполненные строки (игнорируя колонку K)
            List<List<Object>> filledRows = new java.util.ArrayList<>();
            for (List<Object> row : allRows) {
                if (isRowFilled(row)) {
                    filledRows.add(row);
                }
            }
            
            // Возвращаем последние N строк
            if (filledRows.size() <= count) {
                return filledRows;
            }
            
            int startIndex = filledRows.size() - count;
            return filledRows.subList(startIndex, filledRows.size());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to read last filled rows from Google Sheets", e);
        }
    }

    @Override
    public List<List<Object>> readLastFilledRows(int count) {
        return readLastFilledRows(sheetName, count);
    }

    @Override
    public void writeCell(String sheetName, int rowNumber, int columnIndex, String value) {
        try {
            Sheets service = getSheetsService();
            
            // Преобразуем индекс колонки в букву (A, B, C, ..., J)
            String columnLetter = getColumnLetter(columnIndex);
            String range = sheetName + "!" + columnLetter + rowNumber;
            
            ValueRange body = new ValueRange()
                    .setValues(Collections.singletonList(Collections.singletonList(value)));
            
            service.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to write cell to Google Sheets", e);
        }
    }

    @Override
    public void writeCell(int rowNumber, int columnIndex, String value) {
        writeCell(sheetName, rowNumber, columnIndex, value);
    }

    @Override
    public boolean findAndWriteCell(String sheetName, int searchColumnIndex, String searchValue, 
                                     int writeColumnIndex, String writeValue) {
        try {
            List<List<Object>> allRows = readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                return false;
            }
            
            // Ищем строку с нужным значением (начиная со второй строки, пропуская заголовок)
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);
                String cellValue = getCellValue(row, searchColumnIndex);
                
                // Очищаем значения для сравнения (убираем пробелы и дефисы)
                String cleanedSearchValue = searchValue.trim().replaceAll("[\\s-]", "");
                String cleanedCellValue = cellValue.trim().replaceAll("[\\s-]", "");
                
                if (cleanedCellValue.equals(cleanedSearchValue)) {
                    // Нашли строку, записываем значение
                    int rowNumber = i + 1; // Номер строки в таблице (1-based)
                    writeCell(sheetName, rowNumber, writeColumnIndex, writeValue);
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to find and write cell in Google Sheets", e);
        }
    }

    @Override
    public boolean findAndWriteCell(int searchColumnIndex, String searchValue, 
                                     int writeColumnIndex, String writeValue) {
        return findAndWriteCell(sheetName, searchColumnIndex, searchValue, writeColumnIndex, writeValue);
    }

    /**
     * Преобразует индекс колонки в букву (A, B, C, ..., Z, AA, AB, ...)
     */
    private String getColumnLetter(int columnIndex) {
        StringBuilder result = new StringBuilder();
        columnIndex++; // Преобразуем в 1-based для расчета
        
        while (columnIndex > 0) {
            columnIndex--;
            result.insert(0, (char)('A' + (columnIndex % 26)));
            columnIndex /= 26;
        }
        
        return result.toString();
    }
}


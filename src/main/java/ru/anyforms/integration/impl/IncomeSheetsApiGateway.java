package ru.anyforms.integration.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.IncomeSheetsGateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Component
class IncomeSheetsApiGateway implements IncomeSheetsGateway {
    private static final String APPLICATION_NAME = "AmoCRM Webhook Service";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    @Value("${google.sheets.income.spreadsheet.id}")
    private String spreadsheetId;

    @Value("${google.sheets.credentials.json}")
    private String credentialsJson;

    private GoogleCredentials getCredentials() throws IOException {
        if (credentialsJson == null || credentialsJson.trim().isEmpty()) {
            throw new IOException("Service account credentials not found. " +
                    "Please set GOOGLE_APPLICATION_CREDENTIALS environment variable with full JSON content.");
        }

        try (InputStream credentialsStream =
                     new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
            return GoogleCredentials.fromStream(credentialsStream).createScoped(SCOPES);
        }
    }

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = getCredentials();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @Override
    public void ensureSheetExists(String sheetName, List<Object> headerRow) {
        try {
            Sheets service = getSheetsService();

            Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
            boolean exists = spreadsheet.getSheets().stream()
                    .map(Sheet::getProperties)
                    .map(SheetProperties::getTitle)
                    .anyMatch(sheetName::equals);
            if (exists) {
                return;
            }

            BatchUpdateSpreadsheetRequest addSheet = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(new Request()
                            .setAddSheet(new AddSheetRequest()
                                    .setProperties(new SheetProperties().setTitle(sheetName)))));
            service.spreadsheets().batchUpdate(spreadsheetId, addSheet).execute();

            ValueRange header = new ValueRange()
                    .setValues(Collections.singletonList(headerRow));
            service.spreadsheets().values()
                    .update(spreadsheetId, sheetName + "!A1", header)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure sheet exists in Google Sheets", e);
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
    public void appendRows(String sheetName, List<List<Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try {
            Sheets service = getSheetsService();
            ValueRange body = new ValueRange().setValues(rows);

            service.spreadsheets().values()
                    .append(spreadsheetId, sheetName + "!A:Z", body)
                    .setValueInputOption("USER_ENTERED")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to append rows to Google Sheets", e);
        }
    }
}

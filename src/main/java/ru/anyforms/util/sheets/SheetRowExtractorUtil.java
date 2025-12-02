package ru.anyforms.util.sheets;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.GoogleSheetsGateway;
import ru.anyforms.util.LeadUrlExtractor;

import java.util.List;

@AllArgsConstructor
@Component
public class SheetRowExtractorUtil {

    private final GoogleSheetsGateway googleSheetsGateway;

    public Long extractLeadIdFromRow(List<Object> row) {
        // Получаем ссылку на сделку из столбца E
        String dealLink = googleSheetsGateway.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_E_INDEX);

        if (dealLink == null || dealLink.trim().isEmpty()) {
            return null;
        }

        return LeadUrlExtractor.extract(dealLink);
    }
}

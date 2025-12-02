package ru.anyforms.util.amo;

import ru.anyforms.util.amo.DataExtractionService.ExtractedData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataConversionService {
    private final DataFormattingService dataFormattingService;

    public DataConversionService(DataFormattingService dataFormattingService) {
        this.dataFormattingService = dataFormattingService;
    }

    /**
     * Преобразует извлеченные данные в формат строки для Google Sheets
     * Формат: ФИО, Кол-во, Телефон, Инстаграм (пусто), Контакт CRM, ПВЗ СДЭК, Дата оплаты
     */
    public List<Object> convertToGoogleSheetsRow(ExtractedData data) {
        List<Object> rowData = new ArrayList<>();
        
        rowData.add(data.getFio() != null ? data.getFio() : "");
        rowData.add(data.getQuantity() != null ? data.getQuantity() : "");
        rowData.add(data.getPhone() != null ? dataFormattingService.normalizePhone(data.getPhone()) : "");
        rowData.add(""); // Инстаграм - пусто
        rowData.add(data.getCrmLink());
        rowData.add(data.getPvzSdek() != null ? data.getPvzSdek() : "");
        rowData.add(dataFormattingService.formatDate(data.getDatePayment()));
        
        return rowData;
    }
}


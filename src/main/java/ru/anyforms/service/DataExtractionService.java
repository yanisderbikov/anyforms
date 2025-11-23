package ru.anyforms.service;

import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoLead;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DataExtractionService {
    // Field IDs from requirements
    private static final Long FIELD_FIO = 2449809L; // ФИО от контакта
    private static final Long FIELD_PHONE = 2265635L; // Телефон от контакта
    private static final Long FIELD_PVZ_SDEK = 2370939L; // ПВЗ СДЭК от контакта
    private static final Long FIELD_DATE_PAYMENT = 2364807L; // Дата оплаты от сделки
    private static final Long FIELD_QUANTITY = 2351399L; // Кол-во от сделки

    @Value("${amocrm.subdomain:hairdoskeels38}")
    private String subdomain;

    /**
     * Извлекает данные из лида и контакта для дальнейшей обработки
     */
    public ExtractedData extractData(AmoLead lead, AmoContact contact, Long leadId) {
        ExtractedData data = new ExtractedData();
        
        data.setFio(contact.getCustomFieldValue(FIELD_FIO));
        data.setQuantity(lead.getCustomFieldValue(FIELD_QUANTITY));
        data.setPhone(contact.getCustomFieldValue(FIELD_PHONE));
        data.setPvzSdek(contact.getCustomFieldValue(FIELD_PVZ_SDEK));
        data.setDatePayment(lead.getCustomFieldValue(FIELD_DATE_PAYMENT));
        data.setCrmLink("https://" + subdomain + ".amocrm.ru/leads/detail/" + leadId);
        
        return data;
    }

    /**
     * Класс для хранения извлеченных данных
     */
    public static class ExtractedData {
        private String fio;
        private String quantity;
        private String phone;
        private String pvzSdek;
        private String datePayment;
        private String crmLink;

        public String getFio() {
            return fio;
        }

        public void setFio(String fio) {
            this.fio = fio;
        }

        public String getQuantity() {
            return quantity;
        }

        public void setQuantity(String quantity) {
            this.quantity = quantity;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPvzSdek() {
            return pvzSdek;
        }

        public void setPvzSdek(String pvzSdek) {
            this.pvzSdek = pvzSdek;
        }

        public String getDatePayment() {
            return datePayment;
        }

        public void setDatePayment(String datePayment) {
            this.datePayment = datePayment;
        }

        public String getCrmLink() {
            return crmLink;
        }

        public void setCrmLink(String crmLink) {
            this.crmLink = crmLink;
        }
    }
}


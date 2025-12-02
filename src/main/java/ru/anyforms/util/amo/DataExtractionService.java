package ru.anyforms.util.amo;

import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLead;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DataExtractionService {

    @Value("${amocrm.subdomain:hairdoskeels38}")
    private String subdomain;

    /**
     * Извлекает данные из лида и контакта для дальнейшей обработки
     */
    public ExtractedData extractData(AmoLead lead, AmoContact contact, Long leadId) {
        ExtractedData data = new ExtractedData();
        
        data.setFio(contact.getCustomFieldValue(AmoCrmFieldId.FIO.getId()));
        data.setQuantity(lead.getCustomFieldValue(AmoCrmFieldId.QUANTITY.getId()));
        data.setPhone(contact.getCustomFieldValue(AmoCrmFieldId.PHONE.getId()));
        data.setPvzSdek(contact.getCustomFieldValue(AmoCrmFieldId.CONTACT_PVZ.getId()));
        data.setDatePayment(lead.getCustomFieldValue(AmoCrmFieldId.DATE_PAYMENT.getId()));
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


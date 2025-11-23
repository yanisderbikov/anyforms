package ru.anyforms.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AmoWebhook {
    @SerializedName("leads")
    private LeadsEntity leads;
    
    @SerializedName("customers")
    private CustomersEntity customers;
    
    public LeadsEntity getLeads() {
        return leads;
    }

    @Data
    public static class LeadsEntity {
        @SerializedName("call_in")
        private List<LeadEvent> callIn;
        
        @SerializedName("chat")
        private List<LeadEvent> chat;
        
        @SerializedName("site_visit")
        private List<LeadEvent> siteVisit;
        
        @SerializedName("mail_in")
        private List<LeadEvent> mailIn;
        
        @SerializedName("status")
        private List<LeadStatusEvent> status;
        
        public List<LeadEvent> getCallIn() { return callIn; }
        public List<LeadEvent> getChat() { return chat; }
        public List<LeadEvent> getSiteVisit() { return siteVisit; }
        public List<LeadEvent> getMailIn() { return mailIn; }
        public List<LeadStatusEvent> getStatus() { return status; }
    }

    @Data
    public static class CustomersEntity {
        @SerializedName("period")
        List<CustomerPeriodEvent> period;
    }

    @Data
    public static class LeadEvent {
        private Long id;
        private Long pipelineId;
        private Long statusId;
        
        public Long getId() { return id; }
    }

    @Data
    public static class LeadStatusEvent {
        private Long id;
        @SerializedName("old_pipeline_id")
        private Long oldPipelineId;
        @SerializedName("pipeline_id")
        private Long pipelineId;
        @SerializedName("old_status_id")
        private Long oldStatusId;
        @SerializedName("status_id")
        private Long statusId;
        
        public Long getId() { return id; }
    }

    @Data
    public static class CustomerPeriodEvent {
        private Long id;
        @SerializedName("old_period_id")
        private Long oldPeriodId;
        @SerializedName("period_id")
        private Long periodId;
    }
}


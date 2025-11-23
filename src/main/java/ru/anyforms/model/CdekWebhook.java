package ru.anyforms.model;

import com.google.gson.annotations.SerializedName;

public class CdekWebhook {
    @SerializedName("type")
    private String type;
    
    @SerializedName("date_time")
    private String dateTime;
    
    @SerializedName("uuid")
    private String uuid;
    
    @SerializedName("attributes")
    private Attributes attributes;
    
    public static class Attributes {
        @SerializedName("cdek_number")
        private String cdekNumber;
        
        @SerializedName("code")
        private String code;
        
        @SerializedName("name")
        private String name;
        
        @SerializedName("date_time")
        private String dateTime;
        
        @SerializedName("city")
        private String city;
        
        @SerializedName("is_return")
        private Boolean isReturn;
        
        @SerializedName("is_reverse")
        private Boolean isReverse;
        
        @SerializedName("is_client_return")
        private Boolean isClientReturn;
        
        // Getters and setters
        public String getCdekNumber() {
            return cdekNumber;
        }
        
        public void setCdekNumber(String cdekNumber) {
            this.cdekNumber = cdekNumber;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDateTime() {
            return dateTime;
        }
        
        public void setDateTime(String dateTime) {
            this.dateTime = dateTime;
        }
        
        public String getCity() {
            return city;
        }
        
        public void setCity(String city) {
            this.city = city;
        }
        
        public Boolean getIsReturn() {
            return isReturn;
        }
        
        public void setIsReturn(Boolean isReturn) {
            this.isReturn = isReturn;
        }
        
        public Boolean getIsReverse() {
            return isReverse;
        }
        
        public void setIsReverse(Boolean isReverse) {
            this.isReverse = isReverse;
        }
        
        public Boolean getIsClientReturn() {
            return isClientReturn;
        }
        
        public void setIsClientReturn(Boolean isClientReturn) {
            this.isClientReturn = isClientReturn;
        }
    }
    
    // Getters and setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDateTime() {
        return dateTime;
    }
    
    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public Attributes getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }
}


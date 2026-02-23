package ru.anyforms.model.amo;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class AmoContact {
    private Long id;
    private String name;
    @SerializedName("responsible_user_id")
    private Long responsibleUserId;
    @SerializedName("group_id")
    private Long groupId;
    @SerializedName("created_at")
    private Long createdAt;
    @SerializedName("updated_at")
    private Long updatedAt;
    @SerializedName("custom_fields_values")
    private List<CustomField> customFieldsValues;
    private List<Phone> phone;
    private List<Email> email;

    @Data
    public static class CustomField {
        @SerializedName("field_id")
        private Long fieldId;
        @SerializedName("field_name")
        private String fieldName;
        @SerializedName("field_code")
        private String fieldCode;
        @SerializedName("field_type")
        private String fieldType;
        private List<Value> values;

        @Data
        public static class Value {
            private String value;
            @SerializedName("enum_id")
            private Long enumId;
            @SerializedName("enum_code")
            private String enumCode;
        }
    }

    @Data
    public static class Phone {
        private String value;
        @SerializedName("enum_code")
        private String enumCode;
    }

    @Data
    public static class Email {
        private String value;
        @SerializedName("enum_code")
        private String enumCode;
    }

    public String getCustomFieldValue(Long fieldId) {
        if (customFieldsValues == null) return null;
        return customFieldsValues.stream()
                .filter(f -> f.fieldId != null && f.fieldId.equals(fieldId))
                .findFirst()
                .map(f -> f.values != null && !f.values.isEmpty() 
                        ? f.values.get(0).value 
                        : null)
                .orElse(null);
    }

    public String getDefaultPhone() {
        if (phone == null || phone.isEmpty()) return null;
        return phone.get(0).value;
    }
}


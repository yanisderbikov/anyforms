package ru.anyforms.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
@Getter
public class AmoLead {
    private Long id;
    private String name;
    private Long price;
    @SerializedName("responsible_user_id")
    private Long responsibleUserId;
    @SerializedName("group_id")
    private Long groupId;
    @SerializedName("status_id")
    private Long statusId;
    @SerializedName("pipeline_id")
    private Long pipelineId;
    @SerializedName("created_at")
    private Long createdAt;
    @SerializedName("updated_at")
    private Long updatedAt;
    @SerializedName("closed_at")
    private Long closedAt;
    @SerializedName("custom_fields_values")
    private List<CustomField> customFieldsValues;

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

    public Boolean getCustomFieldBoolean(Long fieldId) {
        String value = getCustomFieldValue(fieldId);
        return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }

    public boolean hasCustomFieldEnum(Long fieldId, String enumValue) {
        if (customFieldsValues == null) return false;
        return customFieldsValues.stream()
                .filter(f -> f.fieldId != null && f.fieldId.equals(fieldId))
                .anyMatch(f -> f.values != null && f.values.stream()
                        .anyMatch(v -> enumValue.equals(v.value) || enumValue.equals(v.enumCode)));
    }

    /**
     * Получает количество выбранных значений в мультисписке
     * @param fieldId ID кастомного поля
     * @return количество выбранных значений или 0, если поле не найдено или пусто
     */
    public int getCustomFieldValuesCount(Long fieldId) {
        if (customFieldsValues == null) return 0;
        return customFieldsValues.stream()
                .filter(f -> f.fieldId != null && f.fieldId.equals(fieldId))
                .findFirst()
                .map(f -> f.values != null ? f.values.size() : 0)
                .orElse(0);
    }
}


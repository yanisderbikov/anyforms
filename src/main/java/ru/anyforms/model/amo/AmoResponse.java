package ru.anyforms.model.amo;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class AmoResponse<T> {
    @SerializedName("_embedded")
    private Embedded embedded;

    @Data
    public static class Embedded {
        private List<AmoLead> leads;
        private List<AmoContact> contacts;
    }
}


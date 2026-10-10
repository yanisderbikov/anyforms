package ru.anyforms.model.calculator;

import lombok.Getter;

@Getter
public enum MasterType {
    SLA("SLA, фотополимер"),
    FDM_ABS("FDM, ABS");

    private final String label;

    MasterType(String label) {
        this.label = label;
    }
}

package ru.anyforms.dto.cdek;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Пункт выдачи заказов СДЭК для дропдауна на чекауте. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CdekPvzDTO {
    /** Код ПВЗ в системе СДЭК (например, SPB123). */
    private String code;
    /** Название пункта. */
    private String name;
    /** Город. */
    private String city;
    /** Короткий адрес (улица, дом). */
    private String address;
    /** Полный адрес с городом. */
    private String fullAddress;
    /** Режим работы. */
    private String workTime;
    private Double longitude;
    private Double latitude;
}

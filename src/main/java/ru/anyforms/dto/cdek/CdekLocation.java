package ru.anyforms.dto.cdek;

public record CdekLocation(String countryCode, String postalCode, String city, String address) {

    public static CdekLocation fromPvz(CdekPvzDTO pvz) {
        return new CdekLocation(pvz.getCountryCode(), pvz.getPostalCode(), pvz.getCity(), pvz.getAddress());
    }
}

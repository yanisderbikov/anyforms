package ru.anyforms.service.product;

import ru.anyforms.dto.marketplace.ShopSalesReportDTO;

import java.time.LocalDate;

/**
 * Отчёт по продажам магазина за период (для админки, вкладка товаров).
 * Учитываются только оплаченные заказы витрины (PAID); дата — момент оплаты.
 */
public interface ShopSalesReportService {

    /** Итоги за период [from; to] включительно и разбивка по товарам. Бросает 404, если магазин не найден. */
    ShopSalesReportDTO getReport(String shopSlug, LocalDate from, LocalDate to);
}

package ru.anyforms.service.impl;

import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.CustomProductStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Человекочитаемые строки об изменениях кастомной позиции — для примечаний в сделку АМО. */
final class CustomProductAmoNotes {

    private CustomProductAmoNotes() {
    }

    /** Поля позиции до правки — чтобы потом описать разницу. */
    record Snapshot(String productName, String description, Integer quantity, String modeler,
                    boolean nda, String storageCell) {
        static Snapshot of(CustomProductItem item) {
            return new Snapshot(item.getProductName(), item.getDescription(), item.getQuantity(),
                    item.getModeler(), item.isNda(), item.getStorageCell());
        }
    }

    /** «Котик с крыльями» (AB12CD) */
    static String label(CustomProductItem item) {
        String name = item.getProductName() != null ? item.getProductName() : "без названия";
        return "«" + name + "»" + (item.getPublicId() != null ? " (" + item.getPublicId() + ")" : "");
    }

    static String statusChange(CustomProductStatus from, CustomProductStatus to) {
        return "статус: " + status(from) + " → " + status(to);
    }

    static List<String> created(CustomProductItem item) {
        List<String> lines = new ArrayList<>();
        lines.add("кол-во: " + item.getQuantity() + " шт");
        lines.add("статус: " + status(item.getStatus()));
        if (item.getModeler() != null) {
            lines.add("моделирует: " + item.getModeler());
        }
        if (item.isNda()) {
            lines.add("NDA");
        }
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            lines.add("описание:\n" + item.getDescription());
        }
        return lines;
    }

    static List<String> diff(Snapshot before, CustomProductItem after) {
        List<String> lines = new ArrayList<>();
        if (!Objects.equals(before.productName(), after.getProductName())) {
            lines.add("название: «" + before.productName() + "» → «" + after.getProductName() + "»");
        }
        if (!Objects.equals(before.quantity(), after.getQuantity())) {
            lines.add("кол-во: " + before.quantity() + " → " + after.getQuantity() + " шт");
        }
        if (!Objects.equals(before.modeler(), after.getModeler())) {
            lines.add("моделирует: " + orDash(before.modeler()) + " → " + orDash(after.getModeler()));
        }
        if (before.nda() != after.isNda()) {
            lines.add(after.isNda() ? "NDA: включено" : "NDA: снято");
        }
        if (!Objects.equals(before.storageCell(), after.getStorageCell())) {
            lines.add("ячейка хранения: " + orDash(before.storageCell()) + " → " + orDash(after.getStorageCell()));
        }
        String oldDesc = blankToNull(before.description());
        String newDesc = blankToNull(after.getDescription());
        if (!Objects.equals(oldDesc, newDesc)) {
            if (newDesc == null) {
                lines.add("описание удалено");
            } else {
                lines.add((oldDesc == null ? "описание добавлено" : "описание изменено") + ":\n" + newDesc);
            }
        }
        return lines;
    }

    private static String status(CustomProductStatus status) {
        return status != null ? status.getDescription() : "—";
    }

    private static String orDash(String value) {
        return value != null ? value : "—";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

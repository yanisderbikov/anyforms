package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.CustomProductItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomProductAmoNotesTest {

    private CustomProductItem item() {
        CustomProductItem item = new CustomProductItem();
        item.setProductName("Котик");
        item.setQuantity(3);
        return item;
    }

    @Test
    void diffDescribesChangedFieldsOnly() {
        CustomProductItem item = item();
        var before = CustomProductAmoNotes.Snapshot.of(item);
        item.setQuantity(7);
        item.setDescription("с крыльями");
        item.setNda(true);

        assertEquals(List.of("кол-во: 3 → 7 шт", "NDA: включено", "описание добавлено:\nс крыльями"),
                CustomProductAmoNotes.diff(before, item));
    }

    @Test
    void noChangesNoLines() {
        CustomProductItem item = item();

        assertEquals(List.of(), CustomProductAmoNotes.diff(CustomProductAmoNotes.Snapshot.of(item), item));
    }

    @Test
    void descriptionChangeAndRemoval() {
        CustomProductItem item = item();
        item.setDescription("старое");
        var before = CustomProductAmoNotes.Snapshot.of(item);
        item.setDescription("новое");
        assertEquals(List.of("описание изменено:\nновое"), CustomProductAmoNotes.diff(before, item));

        var before2 = CustomProductAmoNotes.Snapshot.of(item);
        item.setDescription("  ");
        assertEquals(List.of("описание удалено"), CustomProductAmoNotes.diff(before2, item));
    }
}

package ru.anyforms.integration.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.amo.AmoContact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmoCrmHttpGatewayTest {

    private final AmoCrmHttpGateway gateway = new AmoCrmHttpGateway();

    @Test
    void contactEmailAndPhoneComeFromSystemCustomFields() {
        String json = "{\"id\":95248219,\"name\":\"Анна\",\"custom_fields_values\":["
                + "{\"field_id\":2265635,\"field_name\":\"Телефон\",\"field_code\":\"PHONE\",\"field_type\":\"multitext\","
                + "\"values\":[{\"value\":\"+79990001122\",\"enum_id\":1,\"enum_code\":\"WORK\"}]},"
                + "{\"field_id\":2265637,\"field_name\":\"Email\",\"field_code\":\"EMAIL\",\"field_type\":\"multitext\","
                + "\"values\":[{\"value\":\" anna@mail.ru \",\"enum_id\":2,\"enum_code\":\"WORK\"},{\"value\":\"old@mail.ru\",\"enum_code\":\"PRIV\"}]}],"
                + "\"_embedded\":{\"tags\":[],\"companies\":[]}}";

        AmoContact contact = gateway.parseContact(json);

        assertEquals(95248219L, contact.getId());
        assertEquals("anna@mail.ru", contact.getDefaultEmail());
        assertEquals("+79990001122", contact.getDefaultPhone());
    }

    @Test
    void contactWithoutEmailHasNone() {
        AmoContact withPhoneOnly = gateway.parseContact("{\"id\":1,\"custom_fields_values\":["
                + "{\"field_id\":2265635,\"field_code\":\"PHONE\",\"values\":[{\"value\":\"+79990001122\"}]},"
                + "{\"field_id\":2265637,\"field_code\":\"EMAIL\",\"values\":[{\"value\":\"  \"}]}]}");
        AmoContact withoutFields = gateway.parseContact("{\"id\":2,\"custom_fields_values\":null}");

        assertNull(withPhoneOnly.getDefaultEmail());
        assertNull(withoutFields.getDefaultEmail());
        assertNull(withoutFields.getDefaultPhone());
    }
}

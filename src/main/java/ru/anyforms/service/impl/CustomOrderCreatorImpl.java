package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.CustomOrderCreateRequestDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.CustomOrderCreator;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
class CustomOrderCreatorImpl implements CustomOrderCreator {

    private final OrderRepository orderRepository;
    private final AmoCrmGateway amoCrmGateway;

    @Value("${amocrm.custom.pipeline.id}")
    private Long customPipelineId;

    @Value("${amocrm.custom.status.new.id}")
    private Long customStatusNewId;

    @Override
    @Transactional
    public Order create(CustomOrderCreateRequestDTO request) {
        Order order = new Order();
        order.setRetail(false);
        if (request != null) {
            order.setContactName(request.getContactName());
            order.setContactPhone(request.getContactPhone());
            order.setPvzSdekCity(request.getPvzSdekCity());
            order.setPvzSdekStreet(request.getPvzSdekStreet());
            if (request.getDeliveryMethod() != null) {
                order.setDeliveryMethod(request.getDeliveryMethod());
            }
        }
        Order saved = orderRepository.save(order);
        pushToAmo(saved);
        return saved;
    }

    private void pushToAmo(Order order) {
        Long leadId;
        try {
            String name = order.getContactName() != null ? order.getContactName() : "Клиент";
            String phone = order.getContactPhone() != null ? order.getContactPhone() : "";
            leadId = amoCrmGateway.createLead(
                    "Под заказ — " + name, name, phone,
                    customPipelineId, customStatusNewId);
            if (leadId == null) {
                log.error("Под заказ: АМО не вернула id сделки для заказа #{}", order.getId());
                return;
            }
            order.setLeadId(leadId);
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("Под заказ: не удалось создать сделку в АМО для заказа #{}: {}",
                    order.getId(), e.getMessage());
            return;
        }
        fillContactFields(order, leadId);
    }

    private void fillContactFields(Order order, Long leadId) {
        try {
            Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
            if (contactId == null) {
                log.warn("Под заказ: у сделки {} нет контакта — ФИО/ПВЗ не заполнены", leadId);
                return;
            }
            order.setContactId(contactId);
            orderRepository.save(order);

            Map<Long, String> fields = new HashMap<>();
            if (order.getContactName() != null) {
                fields.put(AmoCrmFieldId.FIO_CONTACT.getId(), order.getContactName());
            }
            if (order.getPvzSdekCity() != null) {
                fields.put(AmoCrmFieldId.PVZ_CITY_CONTACT.getId(), order.getPvzSdekCity());
            }
            if (order.getPvzSdekStreet() != null) {
                fields.put(AmoCrmFieldId.PVZ_STREET_CONTACT.getId(), order.getPvzSdekStreet());
            }
            if (!fields.isEmpty()) {
                amoCrmGateway.updateContactCustomField(contactId, fields);
            }
        } catch (Exception e) {
            log.error("Под заказ: не удалось заполнить контакт сделки {}: {}", leadId, e.getMessage());
        }
    }
}

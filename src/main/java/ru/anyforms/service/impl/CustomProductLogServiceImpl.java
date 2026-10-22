package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.CustomProductLogDTO;
import ru.anyforms.event.CustomProductAmoNoteEvent;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.CustomProductLog;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.CustomProductLogRepository;
import ru.anyforms.service.CustomProductLogService;
import ru.anyforms.service.auth.CurrentUserNameResolver;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
class CustomProductLogServiceImpl implements CustomProductLogService {

    private final CustomProductLogRepository logRepository;
    private final CustomProductItemRepository itemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserNameResolver currentUserNameResolver;

    @Override
    @Transactional(readOnly = true)
    public List<CustomProductLogDTO> getByItemId(Long itemId) {
        getItemOrThrow(itemId);
        return logRepository.findByItemIdOrderByCreatedAtAscIdAsc(itemId).stream()
                .map(CustomProductLogServiceImpl::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public CustomProductLogDTO add(Long itemId, String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пустая запись лога");
        }
        CustomProductItem item = getItemOrThrow(itemId);
        CustomProductLog entry = new CustomProductLog();
        entry.setItem(item);
        entry.setText(text.trim());
        CustomProductLog saved = logRepository.save(entry);
        // Есть сделка в АМО — после коммита асинхронно дублируем запись примечанием
        Long leadId = item.getOrder() != null ? item.getOrder().getLeadId() : null;
        if (leadId != null) {
            eventPublisher.publishEvent(new CustomProductAmoNoteEvent(leadId, saved.getCreatedAt(),
                    currentUserNameResolver.currentUserName().orElse(null),
                    "комментарий производства по позиции " + CustomProductAmoNotes.label(item) + ":\n\n"
                            + saved.getText()));
        }
        log.info("CustomProductLog added: id={}, itemId={}", saved.getId(), itemId);
        return toDTO(saved);
    }

    private CustomProductItem getItemOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Позиция не найдена: " + itemId));
    }

    private static CustomProductLogDTO toDTO(CustomProductLog entry) {
        return new CustomProductLogDTO(entry.getId(), entry.getText(), entry.getCreatedAt());
    }
}

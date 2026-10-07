package ru.anyforms.service.calculator.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CalculatorRatesDTO;
import ru.anyforms.dto.calculator.CalculatorRatesVersionDTO;
import ru.anyforms.model.calculator.CalculatorRatesVersion;
import ru.anyforms.repository.GetterCalculatorRates;
import ru.anyforms.repository.SaverCalculatorRates;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.calculator.CalculatorRatesService;
import ru.anyforms.service.calculator.RatesSnapshot;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class CalculatorRatesServiceImpl implements CalculatorRatesService {

    private static final int MAX_HISTORY = 100;

    private final GetterCalculatorRates getterCalculatorRates;
    private final SaverCalculatorRates saverCalculatorRates;
    private final ObjectMapper objectMapper;

    @Override
    public RatesSnapshot current() {
        CalculatorRatesVersion version = getterCalculatorRates.getLatest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Справочник ставок калькулятора пуст"));
        CalculatorRates rates = parse(version);
        List<String> problems = CalculatorRatesValidator.validate(rates);
        if (!problems.isEmpty()) {
            log.error("Справочник ставок v{} неполный: {}", version.getId(), problems);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Справочник ставок неполный: " + String.join("; ", problems));
        }
        return new RatesSnapshot(version.getId(), rates, version.getCreatedAt(), author(version));
    }

    @Override
    public CalculatorRatesDTO get() {
        RatesSnapshot snapshot = current();
        return new CalculatorRatesDTO(snapshot.versionId(), snapshot.rates(), snapshot.updatedAt(), snapshot.updatedBy());
    }

    @Override
    public CalculatorRatesDTO update(CalculatorRates rates, UserAccess user) {
        List<String> problems = CalculatorRatesValidator.validate(rates);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }
        CalculatorRatesVersion saved = saverCalculatorRates.save(CalculatorRatesVersion.builder()
                .rates(write(rates))
                .createdAt(Instant.now())
                .createdByEmail(user.email())
                .createdByName(user.name())
                .build());
        log.info("Ставки калькулятора: новая версия {} от {}", saved.getId(), user.email());
        return new CalculatorRatesDTO(saved.getId(), rates, saved.getCreatedAt(), author(saved));
    }

    @Override
    public List<CalculatorRatesVersionDTO> history(int limit) {
        return getterCalculatorRates.getHistory(Math.min(Math.max(limit, 1), MAX_HISTORY)).stream()
                .map(v -> new CalculatorRatesVersionDTO(v.getId(), v.getCreatedAt(), author(v)))
                .toList();
    }

    private CalculatorRates parse(CalculatorRatesVersion version) {
        try {
            return objectMapper.readValue(version.getRates(), CalculatorRates.class);
        } catch (JsonProcessingException e) {
            log.error("Не удалось прочитать ставки v{}", version.getId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Справочник ставок повреждён");
        }
    }

    private String write(CalculatorRates rates) {
        try {
            return objectMapper.writeValueAsString(rates);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить ставки", e);
        }
    }

    private static String author(CalculatorRatesVersion version) {
        if (version.getCreatedByName() != null && !version.getCreatedByName().isBlank()) {
            return version.getCreatedByName();
        }
        return version.getCreatedByEmail();
    }
}

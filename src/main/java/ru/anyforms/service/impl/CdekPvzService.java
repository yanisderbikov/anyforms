package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.cdek.CdekPvzDTO;
import ru.anyforms.integration.CdekDeliveryPointsGateway;
import ru.anyforms.repository.impl.CdekPvzJdbcRepo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class CdekPvzService {

    private static final Logger logger = LoggerFactory.getLogger(CdekPvzService.class);
    private static final int MAX_RESULTS = 50;
    private static final int MIN_QUERY_LENGTH = 3;
    private static final String HOME_COUNTRY = "RU";

    private final CdekDeliveryPointsGateway deliveryPointsGateway;
    private final CdekPvzJdbcRepo repo;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private volatile List<CdekPvzDTO> snapshot = List.of();

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            List<CdekPvzDTO> stored = repo.findAll();
            if (!stored.isEmpty()) {
                snapshot = List.copyOf(stored);
                logger.info("ПВЗ СДЭК: загружено из БД {} пунктов", stored.size());
                return;
            }
        } catch (Exception e) {
            logger.error("ПВЗ СДЭК: не удалось прочитать кэш из БД: {}", e.getMessage());
        }
        CompletableFuture.runAsync(this::refresh);
    }

    public List<CdekPvzDTO> search(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        List<CdekPvzDTO> points = snapshot;
        if (points.isEmpty()) {
            return List.of();
        }
        String[] tokens = query.trim().toLowerCase().split("[\\s,]+");
        List<CdekPvzDTO> matched = new ArrayList<>();
        for (CdekPvzDTO p : points) {
            if (matches(p, tokens)) {
                matched.add(p);
            }
        }
        matched.sort(Comparator
                .comparing((CdekPvzDTO p) -> HOME_COUNTRY.equalsIgnoreCase(p.getCountryCode()) ? 0 : 1)
                .thenComparing(p -> nullToEmpty(p.getCountryCode()))
                .thenComparing(p -> nullToEmpty(p.getCity()))
                .thenComparing(p -> nullToEmpty(p.getAddress())));
        return matched.size() > MAX_RESULTS ? List.copyOf(matched.subList(0, MAX_RESULTS)) : matched;
    }

    public boolean refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            logger.info("ПВЗ СДЭК: обновление уже идёт, пропускаем");
            return false;
        }
        try {
            List<CdekPvzDTO> fresh = deliveryPointsGateway.fetchAllPickupPoints();
            if (fresh.isEmpty()) {
                logger.warn("ПВЗ СДЭК: список из API пуст, кэш не обновлён ({} пунктов в памяти)", snapshot.size());
                return false;
            }
            repo.replaceAll(fresh);
            snapshot = List.copyOf(fresh);
            logger.info("ПВЗ СДЭК: кэш обновлён, {} пунктов", fresh.size());
            return true;
        } catch (Exception e) {
            logger.error("ПВЗ СДЭК: ошибка обновления кэша: {}", e.getMessage(), e);
            return false;
        } finally {
            refreshing.set(false);
        }
    }

    public int size() {
        return snapshot.size();
    }

    private boolean matches(CdekPvzDTO p, String[] tokens) {
        String haystack = (nullToEmpty(p.getAddress()) + " "
                + nullToEmpty(p.getFullAddress()) + " "
                + nullToEmpty(p.getCity()) + " "
                + nullToEmpty(p.getRegion()) + " "
                + nullToEmpty(p.getName())).toLowerCase();
        for (String token : tokens) {
            if (!token.isBlank() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

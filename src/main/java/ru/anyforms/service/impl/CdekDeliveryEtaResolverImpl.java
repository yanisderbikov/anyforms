package ru.anyforms.service.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.dto.cdek.CdekOrderInfo;
import ru.anyforms.dto.cdek.CdekTariffQuote;
import ru.anyforms.integration.CdekCalculatorGateway;
import ru.anyforms.integration.CdekTrackingGateway;
import ru.anyforms.service.DeliveryEtaResolver;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Log4j2
@Service
class CdekDeliveryEtaResolverImpl implements DeliveryEtaResolver {

    static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final CdekTrackingGateway cdekTrackingGateway;
    private final CdekCalculatorGateway cdekCalculatorGateway;
    private final Clock clock;

    @Autowired
    CdekDeliveryEtaResolverImpl(CdekTrackingGateway cdekTrackingGateway, CdekCalculatorGateway cdekCalculatorGateway) {
        this(cdekTrackingGateway, cdekCalculatorGateway, Clock.systemUTC());
    }

    CdekDeliveryEtaResolverImpl(CdekTrackingGateway cdekTrackingGateway,
                                CdekCalculatorGateway cdekCalculatorGateway,
                                Clock clock) {
        this.cdekTrackingGateway = cdekTrackingGateway;
        this.cdekCalculatorGateway = cdekCalculatorGateway;
        this.clock = clock;
    }

    @Override
    public CdekDeliveryEta resolve(String tracker) {
        if (tracker == null || tracker.isBlank()) {
            return null;
        }
        CdekOrderInfo info;
        try {
            info = cdekTrackingGateway.getOrderInfo(tracker);
        } catch (Exception e) {
            log.error("Failed to load CDEK order info for tracker {}: {}", tracker, e.getMessage(), e);
            return null;
        }
        if (info == null) {
            return null;
        }
        CdekDeliveryEta byDate = CdekDeliveryEta.ofPlannedDate(info.plannedDeliveryDate(), LocalDate.now(clock.withZone(MSK)));
        if (byDate != null) {
            log.info("Delivery ETA for tracker {} from planned date: {}", tracker, byDate.describe());
            return byDate;
        }
        if (!info.canCalculate()) {
            log.info("No planned delivery date and not enough data to calculate ETA for tracker {}", tracker);
            return null;
        }
        try {
            CdekTariffQuote quote = cdekCalculatorGateway.calculate(info.tariffCode(), info.from(), info.to(), info.packages());
            CdekDeliveryEta byPeriod = CdekDeliveryEta.ofPeriod(quote.periodMin(), quote.periodMax());
            if (byPeriod != null) {
                log.info("Delivery ETA for tracker {} from calculator: {}", tracker, byPeriod.describe());
            }
            return byPeriod;
        } catch (Exception e) {
            log.warn("Failed to calculate delivery ETA for tracker {}: {}", tracker, e.getMessage());
            return null;
        }
    }
}

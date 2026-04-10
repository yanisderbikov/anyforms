package ru.anyforms.service.amo.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.service.amo.LandingLeadService;

@Service
@RequiredArgsConstructor
class LandingLeadServiceImpl implements LandingLeadService {
    private final AmoCrmGateway amoCrmGateway;

    @Override
    public Long createLead(String leadName, String name, String phone) {
        return amoCrmGateway.createLandingLead(leadName, name, phone);
    }
}

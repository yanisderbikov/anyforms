package ru.anyforms.service.amo;

import ru.anyforms.dto.LandingLeadRequestDTO;

public interface LandingLeadService {
    Long createLead(LandingLeadRequestDTO request);
}

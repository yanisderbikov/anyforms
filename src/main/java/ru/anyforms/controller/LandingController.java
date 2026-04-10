package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.ApiResponseDTO;
import ru.anyforms.dto.LandingLeadRequestDTO;
import ru.anyforms.service.amo.LandingLeadService;

@RestController
@RequestMapping("/api/landing")
@RequiredArgsConstructor
@Tag(name = "Landing", description = "API для создания заявок с лендинга")
public class LandingController {
    private final LandingLeadService landingLeadService;

    @Operation(summary = "Создать заявку в amoCRM из имени и телефона")
    @PostMapping("/lead")
    public ResponseEntity<ApiResponseDTO> createLead(@Valid @RequestBody LandingLeadRequestDTO request) {
        Long leadId = landingLeadService.createLead(request.getLeadName(), request.getName(), request.getPhone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO(true, null, leadId, null, null));
    }

    @ExceptionHandler({IllegalArgumentException.class, RuntimeException.class})
    public ResponseEntity<ApiResponseDTO> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ApiResponseDTO(false, e.getMessage(), null, null, null));
    }
}

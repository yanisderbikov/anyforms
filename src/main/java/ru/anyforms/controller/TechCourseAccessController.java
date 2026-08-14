package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.payment.CourseAccessDTO;
import ru.anyforms.service.payment.CourseAccessService;

/**
 * Проверка доступа к платформе обучения (edu.anyforms.ru).
 * Учебный сервис спрашивает по почте: покупали курс или нет и какой тариф.
 * Доступ закрыт ролью SERVICE (см. WebSecurityConfig).
 */
@RestController
@RequestMapping("/api/tech/course-access")
@RequiredArgsConstructor
@Tag(name = "TechCourseAccess", description = "Доступ к обучению по почте (межсервисный токен)")
public class TechCourseAccessController {

    private final CourseAccessService courseAccessService;

    @Operation(summary = "Есть ли у почты оплаченный курс",
            description = "Ищет оплаченные (SUCCEEDED) транзакции по продуктам COURSE и COURSE_PERSONAL. "
                    + "Если куплены оба — возвращает PERSONAL")
    @GetMapping
    public ResponseEntity<CourseAccessDTO> getAccess(@RequestParam String email) {
        return ResponseEntity.ok(courseAccessService.getAccess(email));
    }
}

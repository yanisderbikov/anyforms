package ru.anyforms.service.payment.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.CourseAccessDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.CourseAccessService;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
class CourseAccessServiceImpl implements CourseAccessService {

    private static final List<String> COURSE_CODES =
            List.of(PaymentProduct.CODE_COURSE, PaymentProduct.CODE_COURSE_PERSONAL);

    private record AmoAccessTarget(long pipelineId, long statusId) {
    }

    private static final List<AmoAccessTarget> AMO_ACCESS_TARGETS = List.of(
            new AmoAccessTarget(10863606L, 142L),
            new AmoAccessTarget(11188474L, 87796426L)
    );

    private final GetterTransaction getterTransaction;
    private final AmoCrmGateway amoCrmGateway;

    @Override
    public CourseAccessDTO getAccess(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.isEmpty()) {
            return CourseAccessDTO.denied();
        }

        List<PaymentTransaction> paid = getterTransaction.getPaidByEmailAndProductCodes(email, COURSE_CODES);
        if (paid.isEmpty()) {
            return hasAmoAccess(email)
                    ? new CourseAccessDTO(true, CourseAccessDTO.PLAN_SELF, PaymentProduct.CODE_COURSE)
                    : CourseAccessDTO.denied();
        }

        // Личное ведение приоритетнее: если куплены оба тарифа, доступ даём по старшему
        boolean personal = paid.stream()
                .anyMatch(t -> PaymentProduct.CODE_COURSE_PERSONAL.equals(t.getProductCode()));

        return personal
                ? new CourseAccessDTO(true, CourseAccessDTO.PLAN_PERSONAL, PaymentProduct.CODE_COURSE_PERSONAL)
                : new CourseAccessDTO(true, CourseAccessDTO.PLAN_SELF, PaymentProduct.CODE_COURSE);
    }

    private boolean hasAmoAccess(String email) {
        try {
            Long contactId = amoCrmGateway.findContactIdByQuery(email);
            if (contactId == null) {
                return false;
            }
            List<Long> leadIds = amoCrmGateway.getLeadIdsByContact(contactId);
            if (leadIds.isEmpty()) {
                return false;
            }
            boolean granted = leadIds.stream()
                    .map(amoCrmGateway::getLead)
                    .filter(Objects::nonNull)
                    .anyMatch(this::matchesAccessTarget);
            if (granted) {
                log.info("Course access granted via amoCRM for email {} (contact {})", email, contactId);
            }
            return granted;
        } catch (Exception e) {
            log.error("Course access amoCRM fallback failed for email {}", email, e);
            return false;
        }
    }

    private boolean matchesAccessTarget(AmoLead lead) {
        return AMO_ACCESS_TARGETS.stream().anyMatch(t ->
                Objects.equals(lead.getPipelineId(), t.pipelineId())
                        && Objects.equals(lead.getStatusId(), t.statusId()));
    }
}

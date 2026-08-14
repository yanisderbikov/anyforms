package ru.anyforms.service.payment.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.CourseAccessDTO;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.CourseAccessService;

import java.util.List;

@Service
@AllArgsConstructor
class CourseAccessServiceImpl implements CourseAccessService {

    private static final List<String> COURSE_CODES =
            List.of(PaymentProduct.CODE_COURSE, PaymentProduct.CODE_COURSE_PERSONAL);

    private final GetterTransaction getterTransaction;

    @Override
    public CourseAccessDTO getAccess(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.isEmpty()) {
            return CourseAccessDTO.denied();
        }

        List<PaymentTransaction> paid = getterTransaction.getPaidByEmailAndProductCodes(email, COURSE_CODES);
        if (paid.isEmpty()) {
            return CourseAccessDTO.denied();
        }

        // Личное ведение приоритетнее: если куплены оба тарифа, доступ даём по старшему
        boolean personal = paid.stream()
                .anyMatch(t -> PaymentProduct.CODE_COURSE_PERSONAL.equals(t.getProductCode()));

        return personal
                ? new CourseAccessDTO(true, CourseAccessDTO.PLAN_PERSONAL, PaymentProduct.CODE_COURSE_PERSONAL)
                : new CourseAccessDTO(true, CourseAccessDTO.PLAN_SELF, PaymentProduct.CODE_COURSE);
    }
}

package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.CourseAccessDTO;

/** Доступ почты к платформе обучения: покупал ли человек курс и по какому тарифу. */
public interface CourseAccessService {

    CourseAccessDTO getAccess(String email);
}

package ru.anyforms.service.calculator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.anyforms.dto.calculator.OrderCalculationDTO;
import ru.anyforms.dto.calculator.OrderCalculationResult;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.UserAccess;

public final class CalculatorPermissions {

    private CalculatorPermissions() {
    }

    public static boolean isFounder(UserAccess user) {
        return user != null && (user.role() == Role.ADMIN || user.superAdmin());
    }

    public static OrderCalculationResult visibleTo(UserAccess user, OrderCalculationResult result) {
        return isFounder(user) ? result : result.withoutBreakdown();
    }

    public static OrderCalculationDTO visibleTo(UserAccess user, OrderCalculationDTO calculation) {
        if (isFounder(user) || calculation.result() == null || !calculation.result().isObject()) {
            return calculation;
        }
        ObjectNode result = calculation.result().deepCopy();
        result.path("positions").forEach(position -> position.path("options").forEach(option -> {
            if (option instanceof ObjectNode node) {
                node.putNull("price");
                node.putNull("cost");
            }
        }));
        return new OrderCalculationDTO(calculation.entry(), calculation.request(), result, calculation.referenceUrls());
    }
}

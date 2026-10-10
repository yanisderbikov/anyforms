package ru.anyforms.service.amo;

import ru.anyforms.dto.amo.AmoReplyCheckTaskPayload;

public interface MissedReplyChecker {
    void check(AmoReplyCheckTaskPayload payload);
}

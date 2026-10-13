package ru.anyforms.event;

import java.time.Instant;

public record ProductionLogAddedEvent(Long leadId, String text, Instant createdAt) {
}

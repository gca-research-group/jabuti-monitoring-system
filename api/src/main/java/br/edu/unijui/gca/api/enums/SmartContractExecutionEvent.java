package br.edu.unijui.gca.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SmartContractExecutionEvent {
    INBOUND_QUEUE_PUBLISHED("inbound_queue_published"),
    INBOUND_QUEUE_CONSUMED("inbound_queue.consumed"),
    INBOUND_QUEUE_PROCESSING("inbound_queue.processing"),
    INBOUND_QUEUE_PROCESSED("inbound_queue.processed"),

    EXECUTION_QUEUE_PUBLISHED("execution_queue.published"),
    EXECUTION_QUEUE_CONSUMED("execution_queue.consumed"),
    EXECUTION_QUEUE_PROCESSING("execution_queue.processing"),
    EXECUTION_QUEUE_PROCESSED("execution_queue.processed"),

    OUTBOUND_QUEUE_PUBLISHED("outbound_queue.published"),
    OUTBOUND_QUEUE_CONSUMED("outbound_queue.consumed"),
    OUTBOUND_QUEUE_PROCESSING("outbound_queue.processing"),
    OUTBOUND_QUEUE_PROCESSED("outbound_queue.processed");

    private final String key;
}

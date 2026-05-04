package br.edu.unijui.gca.api.config;

public final class QueueNames {
    public static final String MAIN_EXCHANGE = "smart-contract.exchange";

    public static final String INBOUND_QUEUE = "smart-contract.inbound.queue";
    public static final String INBOUND_ROUTING_KEY = "smart-contract.inbound.routing-key";
    public static final String INBOUND_DEAD_LETTER_EXCHANGE = "smart-contract.inbound.dlx";
    public static final String INBOUND_DEAD_LETTER_QUEUE = "smart-contract.inbound.dlq";
    public static final String INBOUND_DEAD_LETTER_QUEUE_ROUTING_KEY = "smart-contract.inbound.dlq.routing-key";

    public static final String EXECUTION_QUEUE = "smart-contract.execution.queue";
    public static final String EXECUTION_ROUTING_KEY = "smart-contract.execution.routing-key";
    public static final String EXECUTION_DEAD_LETTER_EXCHANGE = "smart-contract.execution.dlx";
    public static final String EXECUTION_DEAD_LETTER_QUEUE = "smart-contract.execution.dlq";
    public static final String EXECUTION_DEAD_LETTER_QUEUE_ROUTING_KEY = "smart-contract.execution.dlq.routing-key";

    public static final String OUTBOUND_QUEUE = "smart-contract.outbound.queue";
    public static final String OUTBOUND_ROUTING_KEY = "smart-contract.outbound.routing-key";
    public static final String OUTBOUND_DEAD_LETTER_EXCHANGE = "smart-contract.outbound.dlx";
    public static final String OUTBOUND_DEAD_LETTER_QUEUE = "smart-contract.outbound.dlq";
    public static final String OUTBOUND_DEAD_LETTER_QUEUE_ROUTING_KEY = "smart-contract.outbound.dlq.routing-key";
}

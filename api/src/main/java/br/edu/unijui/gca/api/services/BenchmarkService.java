package br.edu.unijui.gca.api.services;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BenchmarkService {
    @Autowired
    private RabbitListenerEndpointRegistry registry;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    public void start() {
        registry.start();
    }

    public void stop() {
        registry.stop();
    }

    public void purgeAll() {
        List<String> queues = List.of("smart-contract-inbound-queue", "smart-contract-execution-queue", "smart-contract-outbound-queue");

        for (String q : queues) {
            rabbitAdmin.purgeQueue(q, true);
        }
    }
}

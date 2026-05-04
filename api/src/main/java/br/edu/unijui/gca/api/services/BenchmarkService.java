package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
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
        registry.getListenerContainers()
                .forEach(container -> {
                    if (!container.isRunning()) {
                        container.start();
                    }
                });
    }

    public void stop() {
        registry.getListenerContainers()
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });
    }

    public void consumers(int quantity) {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            if (container instanceof SimpleMessageListenerContainer simpleContainer) {
                simpleContainer.setConcurrentConsumers(quantity);
                simpleContainer.setMaxConcurrentConsumers(quantity);
            }
        }
    }

    public void purgeAll() {
        List<String> queues = List.of(QueueNames.INBOUND_QUEUE, QueueNames.EXECUTION_QUEUE, QueueNames.OUTBOUND_QUEUE);

        for (String q : queues) {
            rabbitAdmin.purgeQueue(q, true);
        }
    }
}

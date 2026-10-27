package br.edu.unijui.gca.api.metadata.adapters.rabbitmq;

import br.edu.unijui.gca.api.config.QueueNames;
import lombok.RequiredArgsConstructor;
import br.edu.unijui.gca.api.metadata.application.ports.QueueMaintenance;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Profile("metadata-management")
@Service
public class RabbitMQService implements QueueMaintenance {

    private final RabbitAdmin rabbitAdmin;

    public void purgeAll() {
        List<String> queues = List.of(QueueNames.INBOUND_QUEUE, QueueNames.EXECUTION_QUEUE, QueueNames.OUTBOUND_QUEUE);

        for (String q : queues) {
            rabbitAdmin.purgeQueue(q, true);
        }
    }
}

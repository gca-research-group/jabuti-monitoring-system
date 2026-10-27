package br.edu.unijui.gca.api.postexecution;

import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.postexecution.adapters.http.HttpWebhookDelivery;
import br.edu.unijui.gca.api.postexecution.application.CompleteExecution;
import br.edu.unijui.gca.api.postexecution.application.ports.WebhookDelivery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@Profile("post-execution")
public class PostExecutionConfiguration {

    @Bean
    public WebhookDelivery webhookDelivery() {
        return new HttpWebhookDelivery(HttpClient::newHttpClient);
    }

    @Bean
    public CompleteExecution completeExecution(ExecutionStore store, SmartContractStore contracts, WebhookDelivery webhooks) {
        return new CompleteExecution(store, contracts, webhooks);
    }
}

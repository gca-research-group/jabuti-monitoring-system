package br.edu.unijui.gca.api.pipeline.postexecution.application.ports;

public interface WebhookDelivery {
    void deliver(String url);
}

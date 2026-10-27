package br.edu.unijui.gca.api.postexecution.application.ports;

public interface WebhookDelivery {
    void deliver(String url);
}

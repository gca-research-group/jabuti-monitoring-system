package br.edu.unijui.gca.api.postexecution.adapters.http;

import br.edu.unijui.gca.api.postexecution.application.ports.WebhookDelivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;

public class HttpWebhookDelivery implements WebhookDelivery {

    private final Supplier<HttpClient> clients;

    public HttpWebhookDelivery(Supplier<HttpClient> clients) {
        this.clients = clients;
    }

    @Override
    public void deliver(String url) {
        // Keep delivery synchronous and retain one client per action, as in the original handler.
        try (HttpClient client = clients.get()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                      "message": "Hello, World!"
                    }
                    """))
                    .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

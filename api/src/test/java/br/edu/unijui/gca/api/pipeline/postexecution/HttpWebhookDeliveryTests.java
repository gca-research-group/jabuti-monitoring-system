package br.edu.unijui.gca.api.pipeline.postexecution;

import br.edu.unijui.gca.api.pipeline.postexecution.adapters.http.HttpWebhookDelivery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpWebhookDeliveryTests {

    private final HttpClient client = mock(HttpClient.class);

    @SuppressWarnings("unchecked")
    @Test
    void postsExistingJsonAndClosesTheClientWithoutRejectingHttpErrorResponses() throws Exception {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        new HttpWebhookDelivery(() -> client).deliver("https://example.test/webhook");

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri()).isEqualTo(URI.create("https://example.test/webhook"));
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().headers().firstValue("Content-Type")).contains("application/json");
        assertThat(body(request.getValue())).isEqualTo("{\n  \"message\": \"Hello, World!\"\n}\n");
        verify(client).close();
        verifyNoInteractions(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "interrupted"})
    void transportErrorsRetainTheirWrappingAndCloseTheClient(String error) throws Exception {
        Exception failure = error.equals("io") ? new IOException("delivery failed") : new InterruptedException("delivery interrupted");
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(failure);

        assertThatThrownBy(() -> new HttpWebhookDelivery(() -> client).deliver("https://example.test/webhook"))
                .isExactlyInstanceOf(RuntimeException.class).hasCause(failure);
        verify(client).close();
    }

    @Test
    void invalidUrlRetainsItsOriginalErrorAndStillClosesTheClient() throws Exception {
        assertThatThrownBy(() -> new HttpWebhookDelivery(() -> client).deliver("https://["))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client).close();
        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private String body(HttpRequest request) {
        var bytes = new ByteArrayOutputStream();
        var completed = new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable error) {
                completed.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                completed.complete(bytes.toString(StandardCharsets.UTF_8));
            }
        });
        return completed.join();
    }
}

package com.easychat.common.util;

import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * Bounds both response bytes and the complete exchange, including a stalled response body.
 */
public final class BoundedHttp {
    private BoundedHttp() {
    }

    public static HttpResponse<byte[]> send(HttpClient client, HttpRequest request, int maxBytes, Duration timeout)
            throws IOException, InterruptedException {
        var future = client.sendAsync(request, info -> new LimitedBody(maxBytes));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new HttpTimeoutException("Service response timed out");
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("Service exchange failed", e.getCause());
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private long received;
        private Flow.Subscription subscription;
        private boolean failed;

        LimitedBody(int limit) {
            this.limit = limit;
        }

        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (var buffer : buffers) received += buffer.remaining();
            if (received > limit) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IOException("Service response too large"));
            } else delegate.onNext(buffers);
        }

        public void onError(Throwable error) {
            if (!failed) {
                failed = true;
                delegate.onError(error);
            }
        }

        public void onComplete() {
            if (!failed) delegate.onComplete();
        }
    }
}

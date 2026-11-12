package edu.ucsb.cs156.dining.services;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
final class UCSBApiRetry {

  private static final int MAX_ATTEMPTS = 4;

  private UCSBApiRetry() {}

  static <T> T execute(Supplier<T> request) {
    for (int attempt = 1; ; attempt++) {
      try {
        return request.get();
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || attempt >= MAX_ATTEMPTS) {
          throw e;
        }

        Duration delay = retryDelay(e, attempt);
        log.warn(
            "UCSB API returned 429 on attempt {} of {}; retrying after {}",
            attempt,
            MAX_ATTEMPTS,
            delay);
        try {
          Thread.sleep(delay.toMillis());
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(
              "Interrupted while backing off from UCSB API rate limit", interruptedException);
        }
      }
    }
  }

  static Duration retryDelay(HttpClientErrorException exception, int attempt) {
    String retryAfter =
        exception.getResponseHeaders() == null
            ? null
            : exception.getResponseHeaders().getFirst("Retry-After");
    if (retryAfter != null) {
      try {
        return Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter)));
      } catch (NumberFormatException ignored) {
        try {
          Instant retryAt =
              ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
          Duration delay = Duration.between(Instant.now(), retryAt);
          return delay.isNegative() ? Duration.ZERO : delay;
        } catch (RuntimeException invalidDate) {
          log.warn("Ignoring unparseable UCSB API Retry-After header: {}", retryAfter);
        }
      }
    }

    return Duration.ofSeconds(1L << (attempt - 1));
  }
}

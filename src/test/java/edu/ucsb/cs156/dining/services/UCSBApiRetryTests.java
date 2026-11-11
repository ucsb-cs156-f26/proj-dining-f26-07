package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class UCSBApiRetryTests {

  @Test
  void execute_retries_rate_limited_request() {
    AtomicInteger attempts = new AtomicInteger();
    HttpClientErrorException rateLimitedException = rateLimitedException();

    String result =
        UCSBApiRetry.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw rateLimitedException;
              }
              return "success";
            });

    assertEquals("success", result);
    assertEquals(2, attempts.get());
  }

  @Test
  void execute_stops_after_maximum_rate_limit_attempts() {
    AtomicInteger attempts = new AtomicInteger();
    HttpClientErrorException rateLimitedException = rateLimitedException();

    assertThrows(
        HttpClientErrorException.class,
        () ->
            UCSBApiRetry.execute(
                () -> {
                  attempts.incrementAndGet();
                  throw rateLimitedException;
                }));

    assertEquals(4, attempts.get());
  }

  @Test
  void execute_does_not_retry_other_client_errors() {
    AtomicInteger attempts = new AtomicInteger();
    HttpClientErrorException badRequest =
        HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8);

    assertThrows(
        HttpClientErrorException.class,
        () ->
            UCSBApiRetry.execute(
                () -> {
                  attempts.incrementAndGet();
                  throw badRequest;
                }));

    assertEquals(1, attempts.get());
  }

  @Test
  void execute_uses_backoff_when_retry_after_is_missing() {
    assertInterruptedRetry(rateLimitedException(null));
  }

  @Test
  void execute_uses_backoff_when_retry_after_is_invalid() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Retry-After", "not-a-date");
    assertInterruptedRetry(rateLimitedException(headers));
  }

  @Test
  void execute_obeys_numeric_retry_after() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Retry-After", "-1");
    AtomicInteger attempts = new AtomicInteger();

    String result =
        UCSBApiRetry.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw rateLimitedException(headers);
              }
              return "success";
            });

    assertEquals("success", result);
    assertEquals(2, attempts.get());
  }

  @Test
  void execute_obeys_expired_http_date_retry_after() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        "Retry-After",
        ZonedDateTime.now().minusMinutes(1).format(DateTimeFormatter.RFC_1123_DATE_TIME));
    AtomicInteger attempts = new AtomicInteger();

    String result =
        UCSBApiRetry.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw rateLimitedException(headers);
              }
              return "success";
            });

    assertEquals("success", result);
    assertEquals(2, attempts.get());
  }

  @Test
  void execute_parses_future_http_date_retry_after() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        "Retry-After",
        ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.RFC_1123_DATE_TIME));
    assertInterruptedRetry(rateLimitedException(headers));
  }

  private void assertInterruptedRetry(HttpClientErrorException exception) {
    Thread.currentThread().interrupt();
    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              UCSBApiRetry.execute(
                  () -> {
                    throw exception;
                  }));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private HttpClientErrorException rateLimitedException() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Retry-After", "0");
    return rateLimitedException(headers);
  }

  private HttpClientErrorException rateLimitedException(HttpHeaders headers) {
    return HttpClientErrorException.create(
        HttpStatus.TOO_MANY_REQUESTS,
        "Too Many Requests",
        headers,
        new byte[0],
        StandardCharsets.UTF_8);
  }
}

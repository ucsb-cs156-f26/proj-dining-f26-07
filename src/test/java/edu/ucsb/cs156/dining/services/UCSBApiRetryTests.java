package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
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

  private HttpClientErrorException rateLimitedException() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Retry-After", "0");
    return HttpClientErrorException.create(
        HttpStatus.TOO_MANY_REQUESTS,
        "Too Many Requests",
        headers,
        new byte[0],
        StandardCharsets.UTF_8);
  }
}

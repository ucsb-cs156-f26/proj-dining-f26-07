package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class CurrentMonthMenuItemsLoaderTests {

  private final DiningCommonsService diningCommonsService =
      org.mockito.Mockito.mock(DiningCommonsService.class);
  private final UCSBDiningMenuService diningMenuService =
      org.mockito.Mockito.mock(UCSBDiningMenuService.class);
  private final UCSBDiningMenuItemsService diningMenuItemsService =
      org.mockito.Mockito.mock(UCSBDiningMenuItemsService.class);

  @Test
  void doesNotCallUcsbApiWhenApiKeyIsNotConfigured() throws Exception {
    for (String apiKey : new String[] {"  ", "see-instructions-in-readme", null}) {
      CurrentMonthMenuItemsLoader loader = createLoader(apiKey);
      loader.loadCurrentMonth();
    }

    verify(diningCommonsService, never()).get();
    verify(diningMenuService, never()).getJSON(anyString(), anyString());
    verify(diningMenuItemsService, never()).get(anyString(), anyString(), anyString());
  }

  @Test
  void loadsAndPersistsAvailableMenuItemsForEveryDayInTheCurrentMonth() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get())
        .thenReturn(List.of(DiningCommons.builder().code("ortega").build()));
    when(diningMenuService.getJSON(anyString(), eq("ortega"))).thenReturn("[{\"code\":\"lunch\"}]");
    when(diningMenuItemsService.get(anyString(), eq("ortega"), eq("lunch")))
        .thenReturn(List.of(Entree.builder().name("Pasta").station("Main").build()));

    loader.loadCurrentMonth();

    int daysThisMonth = YearMonth.now().lengthOfMonth();
    verify(diningMenuService, times(daysThisMonth)).getJSON(anyString(), eq("ortega"));
    verify(diningMenuItemsService, times(daysThisMonth))
        .getOrCreateMenuItems(eq("ortega"), eq("lunch"), org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void retriesRateLimitedMenuItemRequests() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get())
        .thenReturn(List.of(DiningCommons.builder().code("ortega").build()));
    when(diningMenuService.getJSON(anyString(), eq("ortega")))
        .thenThrow(rateLimitedWithImmediateRetry())
        .thenReturn("[{\"code\":\"lunch\"}]");
    when(diningMenuItemsService.get(anyString(), eq("ortega"), eq("lunch")))
        .thenThrow(rateLimitedWithImmediateRetry())
        .thenReturn(List.of(Entree.builder().name("Pasta").station("Main").build()));

    loader.loadCurrentMonth();

    int daysThisMonth = YearMonth.now().lengthOfMonth();
    verify(diningMenuService, times(daysThisMonth + 1)).getJSON(anyString(), eq("ortega"));
    verify(diningMenuItemsService, times(daysThisMonth + 1))
        .get(anyString(), eq("ortega"), eq("lunch"));
    verify(diningMenuItemsService, times(daysThisMonth))
        .getOrCreateMenuItems(eq("ortega"), eq("lunch"), anyList());
  }

  @Test
  void skipsMenuItemsWhenTheDiningCommonIsClosed() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get())
        .thenReturn(List.of(DiningCommons.builder().code("ortega").build()));
    when(diningMenuService.getJSON(anyString(), eq("ortega"))).thenReturn(null);

    loader.loadCurrentMonth();

    verify(diningMenuItemsService, never()).get(anyString(), anyString(), anyString());
  }

  @Test
  void rejectsUnexpectedMenuResponseShape() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get())
        .thenReturn(List.of(DiningCommons.builder().code("ortega").build()));
    when(diningMenuService.getJSON(anyString(), eq("ortega"))).thenReturn("{}");

    assertThrows(IllegalStateException.class, loader::loadCurrentMonth);
  }

  @Test
  void skipsMealsWithoutACode() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get())
        .thenReturn(List.of(DiningCommons.builder().code("ortega").build()));
    when(diningMenuService.getJSON(anyString(), eq("ortega"))).thenReturn("[{}]");

    loader.loadCurrentMonth();

    verify(diningMenuItemsService, never()).get(anyString(), anyString(), anyString());
  }

  @Test
  void throwsAfterTheMaximumNumberOfRateLimitRetries() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    HttpClientErrorException.TooManyRequests rateLimit = rateLimitedWithImmediateRetry();
    when(diningCommonsService.get())
        .thenThrow(rateLimit)
        .thenThrow(rateLimit)
        .thenThrow(rateLimit)
        .thenThrow(rateLimit);

    assertThrows(HttpClientErrorException.TooManyRequests.class, loader::loadCurrentMonth);

    verify(diningCommonsService, times(4)).get();
  }

  @Test
  void usesBackoffWhenRetryAfterHeaderIsMissing() throws Exception {
    assertRetriesWith(rateLimitedWithHeaders(null));
  }

  @Test
  void usesBackoffWhenRetryAfterHeaderIsUnrecognized() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "not-a-date");
    assertRetriesWith(rateLimitedWithHeaders(headers));
  }

  @Test
  void acceptsAnExpiredHttpDateInRetryAfter() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.RETRY_AFTER,
        DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(5)));
    assertRetriesWith(rateLimitedWithHeaders(headers));
  }

  @Test
  void acceptsAFutureHttpDateInRetryAfter() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.RETRY_AFTER,
        DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    assertRetriesWith(rateLimitedWithHeaders(headers));
  }

  @Test
  void restoresInterruptStatusWhenInterruptedDuringBackoff() throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get()).thenThrow(rateLimitedWithImmediateRetry());

    try {
      Thread.currentThread().interrupt();

      assertThrows(IllegalStateException.class, loader::loadCurrentMonth);
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private void assertRetriesWith(HttpClientErrorException.TooManyRequests rateLimit)
      throws Exception {
    CurrentMonthMenuItemsLoader loader = createLoader("configured-key");
    when(diningCommonsService.get()).thenThrow(rateLimit).thenReturn(List.of());

    loader.loadCurrentMonth();

    verify(diningCommonsService, times(2)).get();
  }

  private CurrentMonthMenuItemsLoader createLoader(String apiKey) {
    return new CurrentMonthMenuItemsLoader(
        new ObjectMapper(),
        diningCommonsService,
        diningMenuService,
        diningMenuItemsService,
        apiKey);
  }

  private HttpClientErrorException.TooManyRequests rateLimitedWithImmediateRetry() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "0");
    return rateLimitedWithHeaders(headers);
  }

  private HttpClientErrorException.TooManyRequests rateLimitedWithHeaders(HttpHeaders headers) {
    return (HttpClientErrorException.TooManyRequests)
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            headers,
            new byte[0],
            StandardCharsets.UTF_8);
  }
}

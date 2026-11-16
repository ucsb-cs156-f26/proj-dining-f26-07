package edu.ucsb.cs156.dining.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
@Slf4j
public class CurrentMonthMenuItemsLoader {

  private static final int MAX_RATE_LIMIT_RETRIES = 3;
  private static final String SAMPLE_API_KEY = "see-instructions-in-readme";

  private final ObjectMapper objectMapper;
  private final DiningCommonsService diningCommonsService;
  private final UCSBDiningMenuService diningMenuService;
  private final UCSBDiningMenuItemsService diningMenuItemsService;
  private final String apiKey;

  public CurrentMonthMenuItemsLoader(
      ObjectMapper objectMapper,
      DiningCommonsService diningCommonsService,
      UCSBDiningMenuService diningMenuService,
      UCSBDiningMenuItemsService diningMenuItemsService,
      @Value("${app.ucsb.api.consumer_key}") String apiKey) {
    this.objectMapper = objectMapper;
    this.diningCommonsService = diningCommonsService;
    this.diningMenuService = diningMenuService;
    this.diningMenuItemsService = diningMenuItemsService;
    this.apiKey = apiKey;
  }

  public void loadCurrentMonth() throws Exception {
    if (apiKey == null || apiKey.isBlank() || SAMPLE_API_KEY.equalsIgnoreCase(apiKey.trim())) {
      log.warn("Skipping current-month menu load because the UCSB API key is not configured");
      return;
    }

    List<DiningCommons> diningCommons = callWithRateLimitRetry(diningCommonsService::get);
    YearMonth currentMonth = YearMonth.now();

    for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
      String date = currentMonth.atDay(day).toString();
      for (DiningCommons diningCommon : diningCommons) {
        loadMenuItems(date, diningCommon.getCode());
      }
    }
  }

  private void loadMenuItems(String date, String diningCommonCode) throws Exception {
    String menuJson =
        callWithRateLimitRetry(() -> diningMenuService.getJSON(date, diningCommonCode));
    if (menuJson == null) {
      return;
    }

    JsonNode meals = objectMapper.readTree(menuJson);
    if (!meals.isArray()) {
      throw new IllegalStateException(
          "Expected a JSON array of meals for dining common " + diningCommonCode + " on " + date);
    }

    for (JsonNode meal : meals) {
      String mealCode = meal.path("code").asText();
      if (mealCode.isBlank()) {
        log.warn("Skipping meal without a code for dining common {} on {}", diningCommonCode, date);
        continue;
      }

      List<Entree> entrees =
          callWithRateLimitRetry(
              () -> diningMenuItemsService.get(date, diningCommonCode, mealCode));
      diningMenuItemsService.getOrCreateMenuItems(diningCommonCode, mealCode, entrees);
    }
  }

  private <T> T callWithRateLimitRetry(ApiCall<T> apiCall) throws Exception {
    for (int retry = 0; ; retry++) {
      try {
        return apiCall.call();
      } catch (HttpClientErrorException.TooManyRequests e) {
        if (retry >= MAX_RATE_LIMIT_RETRIES) {
          throw e;
        }

        Duration delay = retryDelay(e, retry);
        log.warn("UCSB API rate limited the request; retrying in {} seconds", delay.toSeconds());
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

  private Duration retryDelay(HttpClientErrorException.TooManyRequests exception, int retry) {
    HttpHeaders headers = exception.getResponseHeaders();
    String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (retryAfter != null) {
      try {
        return Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter)));
      } catch (NumberFormatException ignored) {
        try {
          Instant retryAt =
              ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
          Duration delay = Duration.between(Instant.now(), retryAt);
          return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException ignoredDate) {
          log.warn("Ignoring unrecognized UCSB API Retry-After value");
        }
      }
    }

    return Duration.ofSeconds(1L << retry);
  }

  @FunctionalInterface
  private interface ApiCall<T> {
    T call() throws Exception;
  }
}

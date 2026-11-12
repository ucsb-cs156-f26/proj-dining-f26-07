package edu.ucsb.cs156.dining.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
public class MonthlyMenuItemsLoader {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final String DEFAULT_API_KEY = "see-instructions-in-readme";

  private final DiningCommonsService diningCommonsService;
  private final UCSBDiningMenuService diningMenuService;
  private final UCSBDiningMenuItemsService menuItemsService;
  private final ObjectMapper objectMapper;
  private final String apiKey;

  public MonthlyMenuItemsLoader(
      DiningCommonsService diningCommonsService,
      UCSBDiningMenuService diningMenuService,
      UCSBDiningMenuItemsService menuItemsService,
      ObjectMapper objectMapper,
      @Value("${app.ucsb.api.consumer_key}") String apiKey) {
    this.diningCommonsService = diningCommonsService;
    this.diningMenuService = diningMenuService;
    this.menuItemsService = menuItemsService;
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
  }

  public void loadCurrentMonth() {
    loadMonth(YearMonth.now());
  }

  void loadMonth(YearMonth month) {
    if (apiKey == null || apiKey.isBlank() || DEFAULT_API_KEY.equals(apiKey.trim())) {
      log.warn("Skipping monthly menu item download because the UCSB API key is not configured");
      return;
    }

    List<DiningCommons> commons;
    try {
      commons = diningCommonsService.get();
    } catch (JsonProcessingException | RestClientException e) {
      log.error("Unable to retrieve UCSB dining commons for monthly menu item download", e);
      return;
    }

    for (LocalDate date = month.atDay(1);
        !date.isAfter(month.atEndOfMonth());
        date = date.plusDays(1)) {
      String dateString = DATE_FORMAT.format(date);
      for (DiningCommons diningCommon : commons) {
        loadMenuItems(dateString, diningCommon);
      }
    }
  }

  private void loadMenuItems(String date, DiningCommons diningCommon) {
    String commonCode = diningCommon.getCode();
    try {
      String menuJson = diningMenuService.getJSON(date, commonCode);
      if (menuJson == null || menuJson.isBlank()) {
        return;
      }

      JsonNode mealTimes = objectMapper.readTree(menuJson);
      if (!mealTimes.isArray()) {
        log.warn("Ignoring malformed meal-times response for {} on {}", commonCode, date);
        return;
      }

      for (JsonNode mealTime : mealTimes) {
        JsonNode codeNode = mealTime.get("code");
        if (codeNode == null || codeNode.asText().isBlank()) {
          log.warn("Ignoring meal time without a code for {} on {}", commonCode, date);
          continue;
        }
        loadMealItems(date, commonCode, codeNode.asText());
      }
    } catch (JsonProcessingException | RestClientException e) {
      log.error("Unable to load meals for dining common {} on {}", commonCode, date, e);
    }
  }

  private void loadMealItems(String date, String commonCode, String mealCode) {
    try {
      List<Entree> entrees = menuItemsService.get(date, commonCode, mealCode);
      menuItemsService.getOrCreateMenuItems(commonCode, mealCode, entrees);
    } catch (JsonProcessingException | RestClientException | DataAccessException e) {
      log.error(
          "Unable to load menu items for dining common {}, meal {}, on {}",
          commonCode,
          mealCode,
          date,
          e);
    }
  }
}

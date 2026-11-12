package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.entities.MenuItem;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.RestClientException;

class MonthlyMenuItemsLoaderTests {

  private StubDiningCommonsService diningCommonsService;
  private StubDiningMenuService diningMenuService;
  private StubMenuItemsService menuItemsService;

  @BeforeEach
  void setUp() throws Exception {
    diningCommonsService = new StubDiningCommonsService();
    diningMenuService = new StubDiningMenuService();
    menuItemsService = new StubMenuItemsService();
  }

  @Test
  void loadMonth_requests_available_menu_items_for_each_day() {
    MonthlyMenuItemsLoader loader = createLoader("configured-api-key");

    loader.loadMonth(YearMonth.of(2024, 2));

    assertEquals(1, diningCommonsService.calls);
    assertEquals(29, diningMenuService.calls);
    assertEquals(29, menuItemsService.getCalls);
    assertEquals(29, menuItemsService.createCalls);
    assertEquals("2024-02-01", menuItemsService.requestedDates.get(0));
    assertEquals("2024-02-29", menuItemsService.requestedDates.get(28));
    assertEquals("Pasta", menuItemsService.createdItems.get(0));
  }

  @Test
  void loadCurrentMonth_uses_the_current_month() {
    YearMonth currentMonth = YearMonth.now();
    diningMenuService.defaultResponse = "[]";
    MonthlyMenuItemsLoader loader = createLoader("configured-api-key");

    loader.loadCurrentMonth();

    assertEquals(currentMonth.lengthOfMonth(), diningMenuService.calls);
    assertEquals(currentMonth.atDay(1).toString(), diningMenuService.requestedDates.get(0));
    assertEquals(
        currentMonth.atEndOfMonth().toString(),
        diningMenuService.requestedDates.get(diningMenuService.calls - 1));
  }

  @Test
  void loadMonth_skips_requests_when_api_key_is_missing() {
    MonthlyMenuItemsLoader loader = createLoader(null);

    assertDoesNotThrow(() -> loader.loadMonth(YearMonth.of(2024, 2)));

    assertEquals(0, diningCommonsService.calls);
    assertEquals(0, diningMenuService.calls);
    assertEquals(0, menuItemsService.getCalls);
  }

  @Test
  void loadMonth_skips_requests_when_api_key_is_blank() {
    MonthlyMenuItemsLoader loader = createLoader("  ");

    assertDoesNotThrow(() -> loader.loadMonth(YearMonth.of(2024, 2)));

    assertEquals(0, diningCommonsService.calls);
  }

  @Test
  void loadMonth_skips_requests_when_api_key_is_the_default_placeholder() {
    MonthlyMenuItemsLoader loader = createLoader("see-instructions-in-readme");

    assertDoesNotThrow(() -> loader.loadMonth(YearMonth.of(2024, 2)));

    assertEquals(0, diningCommonsService.calls);
    assertEquals(0, diningMenuService.calls);
    assertEquals(0, menuItemsService.getCalls);
  }

  @Test
  void loadMonth_continues_after_unavailable_day() {
    diningMenuService.failedDate = "2024-02-01";
    MonthlyMenuItemsLoader loader = createLoader("configured-api-key");

    loader.loadMonth(YearMonth.of(2024, 2));

    assertEquals(29, diningMenuService.calls);
    assertEquals(28, menuItemsService.getCalls);
  }

  @Test
  void loadMonth_handles_empty_invalid_and_incomplete_meal_responses() {
    diningMenuService.defaultResponse = "[]";
    diningMenuService.responses.put("2024-02-01", null);
    diningMenuService.responses.put("2024-02-02", "  ");
    diningMenuService.responses.put("2024-02-03", "{}");
    diningMenuService.responses.put("2024-02-04", "[");
    diningMenuService.responses.put("2024-02-05", "[{}]");
    diningMenuService.responses.put("2024-02-06", "[{\"code\":\"  \"}]");
    diningMenuService.responses.put("2024-02-07", "[{\"code\":\"lunch\"}]");
    diningMenuService.responses.put("2024-02-08", "[{\"code\":\"lunch\"}]");
    diningMenuService.responses.put("2024-02-09", "[{\"code\":\"lunch\"}]");
    menuItemsService.restFailureDate = "2024-02-07";
    menuItemsService.parseFailureDate = "2024-02-08";
    menuItemsService.saveFailureDate = "2024-02-09";
    MonthlyMenuItemsLoader loader = createLoader("configured-api-key");

    loader.loadMonth(YearMonth.of(2024, 2));

    assertEquals(29, diningMenuService.calls);
    assertEquals(3, menuItemsService.getCalls);
    assertEquals(1, menuItemsService.createCalls);
  }

  @Test
  void loadMonth_stops_if_dining_commons_cannot_be_loaded() {
    diningCommonsService.fail = true;
    MonthlyMenuItemsLoader loader = createLoader("configured-api-key");

    loader.loadMonth(YearMonth.of(2024, 2));

    assertEquals(1, diningCommonsService.calls);
    assertEquals(0, diningMenuService.calls);
  }

  private MonthlyMenuItemsLoader createLoader(String apiKey) {
    return new MonthlyMenuItemsLoader(
        diningCommonsService, diningMenuService, menuItemsService, new ObjectMapper(), apiKey);
  }

  private static class StubDiningCommonsService extends DiningCommonsService {
    private int calls;
    private boolean fail;

    StubDiningCommonsService() {
      super(new RestTemplateBuilder());
    }

    @Override
    public List<DiningCommons> get() {
      calls++;
      if (fail) {
        throw new RestClientException("temporary failure");
      }
      return List.of(DiningCommons.builder().code("ortega").build());
    }
  }

  private static class StubDiningMenuService extends UCSBDiningMenuService {
    private int calls;
    private String failedDate;
    private String defaultResponse = "[{\"name\":\"Lunch\",\"code\":\"lunch\"}]";
    private final List<String> requestedDates = new ArrayList<>();
    private final Map<String, String> responses = new HashMap<>();

    StubDiningMenuService() throws Exception {
      super(new RestTemplateBuilder());
    }

    @Override
    public String getJSON(String dateTime, String diningCommonCode) {
      calls++;
      requestedDates.add(dateTime);
      if (dateTime.equals(failedDate)) {
        throw new RestClientException("temporary failure");
      }
      if (responses.containsKey(dateTime)) {
        return responses.get(dateTime);
      }
      return defaultResponse;
    }
  }

  private static class StubMenuItemsService extends UCSBDiningMenuItemsService {
    private int getCalls;
    private int createCalls;
    private String restFailureDate;
    private String parseFailureDate;
    private String saveFailureDate;
    private final List<String> requestedDates = new ArrayList<>();
    private final List<String> createdItems = new ArrayList<>();

    StubMenuItemsService() throws Exception {
      super(new RestTemplateBuilder());
    }

    @Override
    public List<Entree> get(String dateTime, String diningCommonCode, String mealCode)
        throws JsonProcessingException {
      getCalls++;
      requestedDates.add(dateTime);
      if (dateTime.equals(restFailureDate)) {
        throw new RestClientException("temporary failure");
      }
      if (dateTime.equals(parseFailureDate)) {
        throw new JsonProcessingException("invalid entree response") {};
      }
      return List.of(Entree.builder().name("Pasta").station("Grill").build());
    }

    @Override
    public List<MenuItem> getOrCreateMenuItems(
        String diningCommonCode, String mealCode, List<Entree> entrees) {
      createCalls++;
      if (requestedDates.get(requestedDates.size() - 1).equals(saveFailureDate)) {
        throw new DataAccessResourceFailureException("temporary database failure");
      }
      createdItems.add(entrees.get(0).getName());
      return List.of();
    }
  }
}

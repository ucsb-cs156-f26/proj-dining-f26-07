package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.entities.MenuItem;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
  void loadMonth_skips_requests_when_api_key_is_missing() {
    MonthlyMenuItemsLoader loader = createLoader("  ");

    assertDoesNotThrow(() -> loader.loadMonth(YearMonth.of(2024, 2)));

    assertEquals(0, diningCommonsService.calls);
    assertEquals(0, diningMenuService.calls);
    assertEquals(0, menuItemsService.getCalls);
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

  private MonthlyMenuItemsLoader createLoader(String apiKey) {
    return new MonthlyMenuItemsLoader(
        diningCommonsService, diningMenuService, menuItemsService, new ObjectMapper(), apiKey);
  }

  private static class StubDiningCommonsService extends DiningCommonsService {
    private int calls;

    StubDiningCommonsService() {
      super(new RestTemplateBuilder());
    }

    @Override
    public List<DiningCommons> get() {
      calls++;
      return List.of(DiningCommons.builder().code("ortega").build());
    }
  }

  private static class StubDiningMenuService extends UCSBDiningMenuService {
    private int calls;
    private String failedDate;

    StubDiningMenuService() throws Exception {
      super(new RestTemplateBuilder());
    }

    @Override
    public String getJSON(String dateTime, String diningCommonCode) {
      calls++;
      if (dateTime.equals(failedDate)) {
        throw new RestClientException("temporary failure");
      }
      return "[{\"name\":\"Lunch\",\"code\":\"lunch\"}]";
    }
  }

  private static class StubMenuItemsService extends UCSBDiningMenuItemsService {
    private int getCalls;
    private int createCalls;
    private final List<String> requestedDates = new ArrayList<>();
    private final List<String> createdItems = new ArrayList<>();

    StubMenuItemsService() throws Exception {
      super(new RestTemplateBuilder());
    }

    @Override
    public List<Entree> get(String dateTime, String diningCommonCode, String mealCode) {
      getCalls++;
      requestedDates.add(dateTime);
      return List.of(Entree.builder().name("Pasta").station("Grill").build());
    }

    @Override
    public List<MenuItem> getOrCreateMenuItems(
        String diningCommonCode, String mealCode, List<Entree> entrees) {
      createCalls++;
      createdItems.add(entrees.get(0).getName());
      return List.of();
    }
  }
}

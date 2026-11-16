package edu.ucsb.cs156.dining.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import edu.ucsb.cs156.dining.WebTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("integration")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class ReviewWebIT extends WebTestCase {
  @Test
  public void regular_user_can_create_review() throws Exception {
    setupUser(false);

    page.getByTestId("DiningCommonsTable-cell-row-0-col-meal-lunch").click();

    page.getByTestId("MenuItemTable-cell-row-0-col-Review Item-button").click();

    page.locator("#review-comments").fill("Great oatmeal!");
    page.locator("#review-stars").selectOption("5");
    page.getByText("Submit Review").click();

    assertThat(page.getByText("Review submitted for Oatmeal (vgn)")).isVisible();
  }
}

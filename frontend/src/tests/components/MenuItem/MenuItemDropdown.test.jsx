import { act, render, screen, waitFor } from "@testing-library/react";
import { menuItemFixtures } from "fixtures/menuItemFixtures";
import MenuItemDropdown from "main/components/MenuItem/MenuItemDropdown";
import axios from "axios";
import AxiosMockAdapter from "axios-mock-adapter";
import { vi } from "vitest";

const firstItem = menuItemFixtures.searchResults[0];

// Mock useNavigate from react-router
const mockedNavigate = vi.fn();
vi.mock("react-router", async () => ({
  ...(await vi.importActual("react-router")),
  useNavigate: () => mockedNavigate,
}));

// Remembers the props MenuItemDropdown last passed to AsyncTypeahead, so the
// tests can check them and call onSearch, onChange and renderMenuItemChildren
const typeahead = vi.hoisted(() => ({ props: null }));

// Mock AsyncTypeahead
vi.mock("react-bootstrap-typeahead", () => ({
  AsyncTypeahead: (props) => {
    typeahead.props = props;
    const { inputProps, placeholder } = props;

    // Render a simple input
    return (
      <input
        data-testid={inputProps["data-testid"]}
        placeholder={placeholder}
        aria-label={inputProps["aria-label"]}
      />
    );
  },
}));

describe("MenuItemDropdown tests", () => {
  const axiosMock = new AxiosMockAdapter(axios);

  beforeEach(() => {
    axiosMock.reset();
    axiosMock.resetHistory();
    mockedNavigate.mockClear();
    typeahead.props = null;
  });

  test("renders the search input and starts with no options", () => {
    render(<MenuItemDropdown />);

    expect(
      screen.getByPlaceholderText("Search menu items"),
    ).toBeInTheDocument();
    const input = screen.getByTestId("MenuItemDropdown");
    expect(input).toHaveAttribute("aria-label", "Search menu items");

    expect(typeahead.props.id).toBe("menuItemSearch-typeahead");
    expect(typeahead.props.labelKey).toBe("name");
    expect(typeahead.props.isLoading).toBe(false);
    expect(typeahead.props.options).toEqual([]);
  });

  test("typing searches the backend and the results become the options", async () => {
    axiosMock
      .onGet("/api/diningcommons/menuitem/search")
      .reply(200, menuItemFixtures.searchResults);

    render(<MenuItemDropdown />);

    act(() => {
      typeahead.props.onSearch("stir");
    });

    // while waiting for the backend, it is loading
    expect(typeahead.props.isLoading).toBe(true);

    // once the backend answers, it stops loading and shows the results
    await waitFor(() => {
      expect(typeahead.props.isLoading).toBe(false);
    });
    expect(typeahead.props.options).toEqual(menuItemFixtures.searchResults);

    expect(axiosMock.history.get.length).toBe(1);
    expect(axiosMock.history.get[0].url).toBe(
      "/api/diningcommons/menuitem/search",
    );
    expect(axiosMock.history.get[0].params).toEqual({ query: "stir" });
  });

  test("picking an item goes to its reviews page", () => {
    render(<MenuItemDropdown />);

    typeahead.props.onChange([firstItem]);

    expect(mockedNavigate).toHaveBeenCalledWith("/reviews/1");
  });

  test("clearing the selection does not go anywhere", () => {
    render(<MenuItemDropdown />);

    typeahead.props.onChange([]);

    expect(mockedNavigate).not.toHaveBeenCalled();
  });

  test("each option shows the item name and its dining commons", () => {
    render(<MenuItemDropdown />);

    render(typeahead.props.renderMenuItemChildren(firstItem));

    const name = screen.getByText("Ginger Beef Stir Fry");
    expect(name).toBeInTheDocument();
    expect(screen.getByText("carrillo")).toBeInTheDocument();
    // long names wrap instead of getting cut off
    expect(name.parentElement).toHaveClass("text-wrap");
  });
});

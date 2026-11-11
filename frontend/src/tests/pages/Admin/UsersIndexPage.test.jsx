import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { MemoryRouter } from "react-router";
import AxiosMockAdapter from "axios-mock-adapter";
import axios from "axios";
import UsersIndexPage from "main/pages/Admin/UsersIndexPage";
import { apiCurrentUserFixtures } from "fixtures/currentUserFixtures";
import { systemInfoFixtures } from "fixtures/systemInfoFixtures";
import usersFixtures from "fixtures/usersFixtures";

describe("UsersIndexPage tests", () => {
  const axiosMock = new AxiosMockAdapter(axios);

  const setup = () => {
    axiosMock.reset();
    axiosMock.resetHistory();

    axiosMock
      .onGet("/api/currentUser")
      .reply(200, apiCurrentUserFixtures.adminUser);

    axiosMock
      .onGet("/api/systemInfo")
      .reply(200, systemInfoFixtures.showingNeither);
  };

  const makeUsers = (count) =>
    Array.from({ length: count }, (_, i) => ({
      id: i + 1,
      email: `user${i + 1}@ucsb.edu`,
      givenName: `First${i + 1}`,
      familyName: `Last${i + 1}`,
      admin: false,
      moderator: false,
    }));

  const mockPagedUsers = (users) => {
    axiosMock.onGet("/api/admin/users/paged").reply((config) => {
      const page = Number(config.params.page);
      const size = Number(config.params.size);

      return [
        200,
        {
          content: users.slice(page * size, (page + 1) * size),
          number: page,
          size,
          totalElements: users.length,
          totalPages: Math.ceil(users.length / size),
        },
      ];
    });
  };

  const renderPage = () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <UsersIndexPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
  };

  test("renders without crashing", async () => {
    setup();
    mockPagedUsers([]);
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "Users" }),
    ).toBeInTheDocument();
  });

  test("renders three users in the table", async () => {
    setup();
    mockPagedUsers(usersFixtures.threeUsers);
    renderPage();

    expect(await screen.findByText("phtcon@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByText("pconrad.cis@gmail.com")).toBeInTheDocument();

    expect(screen.getByText("craig.zzyzx@example.org")).toBeInTheDocument();
  });

  test("page size selector has correct options", async () => {
    setup();
    mockPagedUsers([]);
    renderPage();

    const select = screen.getByTestId("UsersIndexPage-pageSize");

    const options = select.querySelectorAll("option");
    const values = Array.from(options).map((o) => o.value);

    expect(values).toEqual(["10", "25", "50", "100", "500"]);
  });

  test("paging fetches the correct users from the backend", async () => {
    setup();
    mockPagedUsers(makeUsers(15));
    renderPage();

    expect(await screen.findByText("user1@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
      "Page 1 of 2",
    );

    expect(screen.queryByText("user11@ucsb.edu")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));

    expect(await screen.findByText("user11@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
      "Page 2 of 2",
    );

    expect(screen.queryByText("user1@ucsb.edu")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(
        axiosMock.history.get.some(
          (request) =>
            request.url === "/api/admin/users/paged" &&
            request.params.page === 1 &&
            request.params.size === 10,
        ),
      ).toBe(true);
    });

    fireEvent.click(screen.getByTestId("UsersIndexPage-prev"));

    expect(await screen.findByText("user1@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
      "Page 1 of 2",
    );
  });

  test("prev and next buttons are disabled for a single page", async () => {
    setup();
    mockPagedUsers(usersFixtures.threeUsers);
    renderPage();

    await screen.findByText("phtcon@ucsb.edu");

    expect(screen.getByTestId("UsersIndexPage-prev")).toBeDisabled();

    expect(screen.getByTestId("UsersIndexPage-next")).toBeDisabled();
  });

  test("prev from page 3 goes to page 2", async () => {
    setup();
    mockPagedUsers(makeUsers(21));
    renderPage();

    await screen.findByText("user1@ucsb.edu");

    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));

    await screen.findByText("user11@ucsb.edu");

    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));

    expect(await screen.findByText("user21@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
      "Page 3 of 3",
    );

    fireEvent.click(screen.getByTestId("UsersIndexPage-prev"));

    expect(await screen.findByText("user11@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
      "Page 2 of 3",
    );

    expect(screen.queryByText("user1@ucsb.edu")).not.toBeInTheDocument();

    expect(screen.queryByText("user21@ucsb.edu")).not.toBeInTheDocument();
  });

  test("changing page size resets to first page", async () => {
    setup();
    mockPagedUsers(makeUsers(15));
    renderPage();

    await screen.findByText("user1@ucsb.edu");

    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));

    await screen.findByText("user11@ucsb.edu");

    fireEvent.change(screen.getByTestId("UsersIndexPage-pageSize"), {
      target: { value: "25" },
    });

    expect(await screen.findByText("user1@ucsb.edu")).toBeInTheDocument();

    expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
      "Page 1 of 1",
    );

    await waitFor(() => {
      expect(
        axiosMock.history.get.some(
          (request) =>
            request.url === "/api/admin/users/paged" &&
            request.params.page === 0 &&
            request.params.size === 25,
        ),
      ).toBe(true);
    });
  });
});

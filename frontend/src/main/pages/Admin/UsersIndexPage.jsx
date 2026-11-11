import { useState } from "react";
import { Form } from "react-bootstrap";
import BasicLayout from "main/layouts/BasicLayout/BasicLayout";
import UsersTable from "main/components/Users/UsersTable";
import { useBackend } from "main/utils/useBackend";

const pageSizeOptions = [10, 25, 50, 100, 500];

export default function UsersIndexPage() {
  const [pageSize, setPageSize] = useState(10);
  const [currentPage, setCurrentPage] = useState(0);

  const { data: userPage, isFetching } = useBackend(
    // Stryker disable next-line StringLiteral: cache key is an internal React Query detail
    ["/api/admin/users/paged", currentPage, pageSize],
    {
      // Stryker disable next-line StringLiteral: Axios defaults to GET
      method: "GET",
      url: "/api/admin/users/paged",
      params: {
        page: currentPage,
        size: pageSize,
      },
    },
  );
  // Stryker disable next-line ArrayDeclaration: empty array is the default loading state
  const users = userPage?.content ?? [];
  const totalPages = userPage?.totalPages ?? 0;

  const handlePageSizeChange = (e) => {
    setPageSize(Number(e.target.value));
    setCurrentPage(0);
  };

  return (
    <BasicLayout>
      <div className="pt-2">
        <h1>Users</h1>

        <Form.Group className="mb-3 d-flex align-items-center gap-2">
          <Form.Label className="mb-0">Page Size:</Form.Label>
          <Form.Select
            style={{ width: "auto" }}
            value={pageSize}
            onChange={handlePageSizeChange}
            data-testid="UsersIndexPage-pageSize"
          >
            {pageSizeOptions.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </Form.Select>
        </Form.Group>

        <UsersTable users={users} />

        <div className="d-flex align-items-center gap-2 mt-2">
          <button
            className="btn btn-outline-secondary"
            onClick={() => setCurrentPage((p) => Math.max(p - 1, 0))}
            disabled={currentPage === 0 || isFetching}
            data-testid="UsersIndexPage-prev"
          >
            Previous
          </button>

          <span data-testid="UsersIndexPage-page-info">
            Page {currentPage + 1} of {Math.max(totalPages, 1)}
          </span>

          <button
            className="btn btn-outline-secondary"
            onClick={() => setCurrentPage((p) => p + 1)}
            disabled={isFetching || currentPage >= totalPages - 1}
            data-testid="UsersIndexPage-next"
          >
            Next
          </button>
        </div>
      </div>
    </BasicLayout>
  );
}

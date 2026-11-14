import { AsyncTypeahead } from "react-bootstrap-typeahead";
import { Form } from "react-bootstrap";
import { useState } from "react";
import { useNavigate } from "react-router";
import axios from "axios";
import "react-bootstrap-typeahead/css/Typeahead.css";

export default function MenuItemDropdown() {
  const [isLoading, setIsLoading] = useState(false);
  const [options, setOptions] = useState([]);
  const navigate = useNavigate();

  const handleSearch = async (query) => {
    setIsLoading(true);
    const response = await axios.get("/api/diningcommons/menuitem/search", {
      params: { query },
    });
    setOptions(response.data);
    setIsLoading(false);
  };

  const handleSelectionChange = (selected) => {
    if (selected.length > 0) {
      navigate(`/reviews/${selected[0].id}`);
    }
  };

  return (
    <Form.Group controlId="menuItemSearch">
      <AsyncTypeahead
        id="menuItemSearch-typeahead"
        options={options}
        labelKey="name"
        placeholder="Search menu items"
        onChange={handleSelectionChange}
        highlightOnlyResult
        isLoading={isLoading}
        onSearch={handleSearch}
        renderMenuItemChildren={(item) => (
          <div className="text-wrap">
            <div>{item.name}</div>
            <small className="text-muted">{item.diningCommonsCode}</small>
          </div>
        )}
        inputProps={{
          "aria-label": "Search menu items",
          "data-testid": "MenuItemDropdown",
        }}
      />
    </Form.Group>
  );
}

const menuItemFixtures = {
  oneMenuItem: [
    {
      id: 1,
      name: "Oatmeal (vgn)",
      station: "Grill (Cafe)",
      reviews: [{ itemsStars: 4 }, { itemsStars: 5 }, { itemsStars: 3 }],
    },
  ],
  fiveMenuItems: [
    {
      id: 1,
      name: "Oatmeal (vgn)",
      station: "Grill (Cafe)",
      reviews: [{ itemsStars: 4 }, { itemsStars: 5 }],
    },
    {
      id: 2,
      name: "Blintz with Strawberry Compote (v)",
      station: "Grill (Cafe)",
      reviews: [],
    },
    {
      id: 3,
      name: "Cage Free Scrambled Eggs (v)",
      station: "Grill (Cafe)",
      reviews: [{ itemsStars: 3 }, { itemsStars: 4 }],
    },
    {
      id: 4,
      name: "Cage Free Scrambled Egg Whites (v)",
      station: "Grill (Cafe)",
      reviews: [{ itemsStars: 5 }],
    },
    {
      id: 5,
      name: "Sliced Potato with Onions (vgn)",
      station: "Grill (Cafe)",
      reviews: null,
    },
  ],
  // what /api/diningcommons/menuitem/search?query=stir returns
  searchResults: [
    {
      id: 1,
      name: "Ginger Beef Stir Fry",
      diningCommonsCode: "carrillo",
      mealCode: "dinner",
      station: "Mongolian Grill",
      reviews: [],
    },
    {
      id: 2,
      name: "Tofu & Vegetable Stir Fry (vgn)",
      diningCommonsCode: "carrillo",
      mealCode: "dinner",
      station: "Mongolian Grill",
      reviews: [],
    },
  ],
};

export { menuItemFixtures };

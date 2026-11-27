import React from "react";

import RoleBadge from "main/components/Profile/RoleBadge";
import { currentUserFixtures } from "fixtures/currentUserFixtures";

export default {
  title: "components/Profile/RoleBadge",
  component: RoleBadge,
  tags: ["autodocs"],
};

const Template = (args) => {
  return <RoleBadge {...args} />;
};

export const UserRole = Template.bind({});
UserRole.args = {
  role: "ROLE_USER",
  currentUser: currentUserFixtures.userOnly,
};

export const AdminRole = Template.bind({});
AdminRole.args = {
  role: "ROLE_ADMIN",
  currentUser: currentUserFixtures.adminUser,
};

export const MissingRole = Template.bind({});
MissingRole.args = {
  role: "ROLE_ADMIN",
  currentUser: currentUserFixtures.userOnly,
};

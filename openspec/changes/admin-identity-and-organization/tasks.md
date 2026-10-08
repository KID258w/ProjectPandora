## 1. Organization and account foundations

- [ ] 1.1 Define administrator and business-account identity, primary business role, department/team placement, and leader relationships.
- [ ] 1.2 Implement organization/account activation and deactivation with referential and active-descendant validation; preserve historical records.
- [ ] 1.3 Add administrator-operation audit persistence with sensitive-field redaction.

## 2. Authorization and management APIs

- [ ] 2.1 Implement isolated administrator authentication, RBAC checks, and organization-scope validation for management endpoints.
- [ ] 2.2 Implement department and team CRUD, leader assignment/replacement, and deactivation safeguards.
- [ ] 2.3 Implement business-account creation/editing, primary-role and placement assignment, activation/deactivation, and password reset.
- [ ] 2.4 Implement filtered administrator audit-record queries; ensure administrator APIs cannot access business tasks or employee logs.
- [ ] 2.5 Publish and verify OpenAPI/Swagger contracts for supported administrator endpoints.

## 3. Web management console

- [ ] 3.1 Build administrator sign-in and workbench with system-management summaries only.
- [ ] 3.2 Build department/team management pages and leader-assignment workflows.
- [ ] 3.3 Build business-account and role-management pages, including activation and password-reset feedback.
- [ ] 3.4 Build audit-record list, filters, and detail presentation with sensitive values masked.

## 4. Verification

- [ ] 4.1 Add unit and API tests for role isolation, uniqueness, organization placement, leader reassignment, and deactivation safeguards.
- [ ] 4.2 Verify administrator endpoints reject task/log reads and audit responses never expose passwords or log content.
- [ ] 4.3 Verify successful and rejected management operations produce the specified audit behavior and clear UI feedback.

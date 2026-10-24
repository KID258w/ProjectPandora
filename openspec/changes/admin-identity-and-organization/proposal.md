## Why

Issue #1 account-creation and phone-login residuals are complete. The remaining Issue2 work is to let administrators maintain organization and employee business relationships through Web, and let employees maintain their own personal profile and password through Android App.

## What Changes

- Add department and team detail, name/description editing, activation controls, and server-side safeguards for deactivation. Organization details show leaders and members read-only; a team's department remains fixed after creation.
- Add employee filtering and detail reads, plus administrator updates limited to business role, department/team placement, and activation state. Web does not edit employee personal fields or reset passwords.
- Permit zero or one active department manager per department and zero or one active team leader per team. Leader changes happen through employee management; the backend owns direct-manager links and allows a temporary vacancy.
- Allow an employee to update their own name, phone, identity number, and password in the App. Name and identity number share one editable profile form and may be saved together, while remaining independent fields; a phone change updates the login identifier stored in `username`.
- Define the corresponding backend model, API, validation, authorization, and sensitive-data requirements. Continue to exclude business tasks and employee logs from administrator access.
- Update the backend API/database/provisioning docs, requirements, Web and Android interface docs, and this OpenSpec change.

## Non-Goals

- Reworking Issue #1 account creation, initial-password generation, Android login wording, or the Web organization navigation control.
- Web edits to employee name, phone, identity number, or password; Web password reset; SMS verification; or password recovery.
- Changing a team's parent department, automatic leader succession, organization-transfer history, or administrative access to business tasks and employee logs.
- Company-important-item workflows, which remain in their separate change.

## Capabilities

### New Capabilities
- `admin-identity-and-organization`: department, team, and employee maintenance plus employee App self-service.

### Modified Capabilities
- None.

## Impact

- Backend models and APIs for `User`, `Department`, and `Team`, plus the administrator audit entity and query.
- Web organization/employee maintenance and App employee profile/password screens.
- `docs/backend/api-design.md`, `docs/backend/database-design.md`, `docs/backend/account-provisioning.md`, `docs/requirements/05-web-admin-console.md`, `docs/web/account-management-ui.md`, `docs/android/account-login-ui.md`, and the change artifacts in this directory.
- PR plan: one backend PR first, followed by Web and Android PRs that may proceed in parallel; three PRs total.

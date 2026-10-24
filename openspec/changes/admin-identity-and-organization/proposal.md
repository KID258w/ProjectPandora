## Why

Issue #6 provides the initial administrator, organization, and App authentication foundation, but the agreed account-management behavior and the remaining Web/Android login details are not complete. Issue2 must align account creation with phone-number login and required identity data, then provide the organization and employee maintenance workflows needed by administrators without giving the Web console control over employee personal credentials.

## What Changes

- Close Issue #1 residuals: align the Web employee form with phone-number login, require an identity number, remove password and activation inputs, show a one-time backend-generated initial password after creation, fix the organization menu disclosure icon, and change Android login wording from account to phone number.
- Extend department and team maintenance with detail information, name/description editing, activation controls, and active-descendant safeguards. Department/team members and leaders are displayed read-only in organization details; team department placement is chosen at creation and is not editable later.
- Extend employee management with filtering, masked identity-number display, activation controls, and server-validated changes to business role and department/team placement. Web cannot edit name, phone number, identity number, or password after creation and cannot reset passwords.
- Use the existing unique `username` field as the business user's phone number. Require identity number when creating a new business account, encrypt it at rest, and avoid exposing its full value in ordinary Web or App profile reads.
- Generate a strong random initial password on the backend, store only its password hash, automatically activate the new account, and return the plaintext once in the successful creation response for a non-cached Web result dialog. Exclude it from logs, browser storage, audit records, and later reads.
- Allow zero or one active department manager per department and zero or one active team leader per team. Manage leader changes through employee role/placement changes; keep direct-report relationships server-owned and permit a temporary vacancy.
- Allow employees to update their own name, phone number, identity number, and password in the Android App. No SMS verification is required for the MVP. Password recovery is not defined by this change.

## Capabilities

### New Capabilities
- `admin-identity-and-organization`: Administrator identity, organization and employee management, account provisioning, and management-operation auditing.

### Modified Capabilities
- None.

## Impact

- `docs/backend/api-design.md`, `docs/backend/database-design.md`, `docs/backend/account-provisioning.md`, `docs/requirements/05-web-admin-console.md`, the Issue #1 sections in `docs/android/account-login-ui.md` and `docs/web/account-management-ui.md`, and this OpenSpec change.
- Backend account and organization models, migrations, validation, administrator APIs, App self-service APIs, and audit redaction.
- Web organization and employee management, one-time initial-password presentation, and the organization navigation control.
- Android login copy and employee self-service account/profile screens.
- Existing `/api/v1/admin/users/`, `/api/v1/admin/users/{id}/`, `/api/v1/app/auth/token/`, and `/api/v1/app/me/` contracts need the Issue2 changes described in the design and API documents. The App login JSON key remains `username`; for business users its value becomes the current phone number.
- Company-important-item management remains owned by its separate change. Task and employee-log data remain unavailable to administrators.

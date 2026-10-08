## Context

The Web console must provision business accounts and their organization scopes before mobile task, log, and view permissions can be enforced. The console also manages organization structure and administrator audit records. Product requirements explicitly exclude business task and employee-log access from the administrator role.

## Goals / Non-Goals

**Goals:**
- Provide an isolated administrator sign-in and Web management scope.
- Maintain departments, teams, business accounts, primary roles, and manager assignments while preserving historical records.
- Enforce authorization on every management API and record administrator changes.

**Non-Goals:**
- Managing company-important-item content (handled by `company-important-items`).
- Viewing or operating on business tasks, employee logs, or AI conversations.
- Defining password complexity, account lockout, or final database column details in this change.

## Decisions

- Keep System Administrator identity and permissions distinct from the four mobile business roles. Enforce the distinction in backend authorization, not only in Web navigation.
- Represent department/team membership and responsibility relationships explicitly so role scope can be resolved consistently by downstream task, log, and view services. The exact relational schema remains an implementation detail.
- Use deactivate/soft-disable semantics instead of physical deletion for accounts and organizational units; this preserves history and allows referential integrity.
- Validate leader replacement and active descendants/members before deactivation. Apply a successful reassignment or deactivation atomically so no partially updated scope is visible.
- Write audit records for successful management mutations. Redact secrets such as passwords and exclude business task/log content.
- Limit the workbench and all admin queries to system-management data. Reject task and employee-log access at the API boundary.

## Alternatives Considered

- Sharing one account model and relying only on role labels was rejected because administrators must never inherit mobile business access; separate authorization boundaries are easier to audit.
- Physical deletion of users or organizations was rejected because it breaks historical references; deactivation preserves history while blocking future access.

## Risks / Trade-offs

- [Stale or overlapping organization assignments could grant excessive scope] → Enforce one primary business role and validate organization placement and leader uniqueness on the server.
- [Account or leader deactivation could orphan active work] → Block deactivation until an eligible replacement or reassignment is recorded; keep historical ownership unchanged.
- [Audit payloads could expose sensitive data] → Use an allowlist of auditable fields and never store plaintext credentials or employee-log content.

## Migration Plan

This is a foundational capability for a new system. Create administrator and business-account records, organization records, and audit storage before dependent task/log/view features. If seeded data is used, validate unique accounts and organization references before enabling mobile access. Rollback by disabling the admin console and restoring the previous deployment; do not delete historical records.

## Open Questions

- Confirm the initial administrator provisioning procedure and whether more than one administrator account is required for the course MVP.
- Confirm final password reset and validation rules during implementation design.

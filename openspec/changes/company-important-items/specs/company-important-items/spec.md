## ADDED Requirements

### Requirement: Administrators manage company-important items
The system SHALL allow an authenticated administrator to create and edit company-important items with a required title, summary, and body, optionally attach media, save drafts, publish, unpublish, and manually reorder items. The system SHALL NOT provide pinning or task-management fields for these items.

#### Scenario: Administrator saves a draft
- **WHEN** an administrator saves a valid item as a draft
- **THEN** the system stores it for administration and excludes it from all mobile-user lists

#### Scenario: Administrator publishes an item
- **WHEN** an administrator publishes an item with all required fields and fewer than ten items are published
- **THEN** the system publishes it, records publisher and publication time, and writes an audit record

#### Scenario: Required content is missing
- **WHEN** an administrator attempts to publish an item without a title, summary, or body
- **THEN** the system rejects publication and identifies the missing required content

### Requirement: Published items are common and limited on mobile
The system SHALL show the same published company-important items to every authenticated mobile business user, ordered by the administrator-managed sequence, with no more than ten items. Drafts and unpublished items SHALL NOT be returned to mobile users.

#### Scenario: Mobile user views published items
- **WHEN** any authenticated mobile business user opens the company-important-items panel
- **THEN** the system returns up to ten currently published items in administrator-defined order with title, summary, publication time, and detail access

#### Scenario: Administrator reaches the published-item limit
- **WHEN** ten items are already published and an administrator attempts to publish another
- **THEN** the system rejects publication and requires an existing item to be unpublished first

#### Scenario: Administrator unpublishes an item
- **WHEN** an administrator unpublishes a published item
- **THEN** it disappears from mobile results while its record and audit history remain available in administration

### Requirement: Company-important items remain separate from tasks
The system SHALL treat company-important items as informational content and SHALL NOT expose task assignment, owner, progress, hierarchy, completion, or acceptance operations for them.

#### Scenario: User opens an item detail
- **WHEN** a mobile user opens a published company-important item
- **THEN** the system displays its informational body and optional attachments without task controls

#### Scenario: Non-administrator attempts content management
- **WHEN** a non-administrator attempts to create, edit, reorder, publish, or unpublish an item
- **THEN** the system rejects the operation without changing the item

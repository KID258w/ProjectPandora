## ADDED Requirements

### Requirement: Administrators manage company-important items
The system SHALL allow an authenticated administrator to create and edit company-important items with a required title, summary, and body, save drafts, publish, unpublish, and manually reorder items. The system SHALL NOT provide pinning or task-management fields for these items.

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
The system SHALL return the same published items to all authenticated mobile business users, in administrator order and capped at ten. Home SHALL preview the first five and link to the list. Each list item SHALL expand inline to show its full body. The current mobile flow SHALL NOT open a separate item detail screen or display/download attachments. Draft and offline items SHALL NOT be returned.

#### Scenario: Mobile user views published items
- **WHEN** any authenticated mobile business user opens the Home company-important-items card and then selects “View all”
- **THEN** Home previews the first five currently published items, and the secondary list shows up to ten in administrator-defined order; expanding a row reveals its full body in the same list screen

#### Scenario: Administrator reaches the published-item limit
- **WHEN** ten items are already published and an administrator attempts to publish another
- **THEN** the system rejects publication and requires an existing item to be unpublished first

#### Scenario: Administrator unpublishes an item
- **WHEN** an administrator unpublishes a published item
- **THEN** it disappears from mobile results while its record and audit history remain available in administration

### Requirement: Company-important items remain separate from tasks
The system SHALL treat company-important items as informational content and SHALL NOT expose task assignment, owner, progress, hierarchy, completion, or acceptance operations for them.

#### Scenario: User expands a company item
- **WHEN** a mobile user expands a published item in the secondary list
- **THEN** the system displays its informational body inline without navigating to another screen or requiring attachments

#### Scenario: Non-administrator attempts content management
- **WHEN** a non-administrator attempts to create, edit, reorder, publish, or unpublish an item
- **THEN** the system rejects the operation without changing the item

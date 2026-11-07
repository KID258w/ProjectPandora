## ADDED Requirements

### Requirement: Users manage their own pinned tasks

The system SHALL persist pinned tasks per App user. A user MAY pin only an approved task that they created or are assigned. Pinning SHALL NOT alter task state, create notifications, or write operation-audit entries.

#### Scenario: User pins an accessible approved task
- **WHEN** the user pins a task they created or are assigned and it is approved
- **THEN** the system stores one pin for that user and task

#### Scenario: User attempts to pin an inaccessible or pending task
- **WHEN** the user pins a pending task or a task they neither created nor are assigned
- **THEN** the system rejects the request without creating a pin

#### Scenario: User repeats a pin or unpin request
- **WHEN** the user pins an already pinned task or unpins a task that is not pinned
- **THEN** the operation succeeds without duplicate records or an error

### Requirement: Task Center prioritizes pinned tasks

The ordinary Task Center SHALL display all of the user's pinned tasks below the page title and above filters and ordinary results. The pinned section SHALL be hidden when empty and SHALL ignore ordinary list filters. A pinned task SHALL NOT be duplicated in the ordinary results.

#### Scenario: User has no pinned tasks
- **WHEN** the ordinary Task Center loads and the user has no pinned tasks
- **THEN** no pinned section or placeholder is displayed

#### Scenario: User has pinned tasks while filters are active
- **WHEN** the ordinary Task Center loads with one or more pins and any list filter is active
- **THEN** all pinned tasks appear above the filtered results, ordered by most recent pin, and are omitted from those results

#### Scenario: Pinned task becomes completed or cancelled
- **WHEN** a pinned task becomes completed or cancelled
- **THEN** its pin remains and it stays visible in the pinned section

### Requirement: Users pin tasks with a long press

In the user's ordinary Task Center list, a long press SHALL present the applicable pin or unpin action. A short tap SHALL continue to open the existing task detail. The Approval Center and read-only subordinate task lists SHALL NOT offer pin actions or the pinned section.

#### Scenario: User long-presses an unpinned task
- **WHEN** the user long-presses an eligible unpinned task row
- **THEN** an action to pin that task is presented

#### Scenario: User long-presses a pinned task
- **WHEN** the user long-presses a task in the pinned section
- **THEN** an action to unpin that task is presented

#### Scenario: User taps a task row
- **WHEN** the user taps a task row without a long press
- **THEN** the existing task detail opens

# Spec Delta

## Purpose

为 Android App 提供一套公司共享标签，用于分类任务和日志，并让工作视图按任务标签稳定着色。标签管理仅向创始人开放，标签关联遵循任务和日志既有权限。

## ADDED Requirements

### Requirement: All App roles can select shared enabled tags
The system SHALL provide one company-wide tag catalogue. Every App role SHALL be able to search and select one or more enabled tags only in task or log forms they are already authorized to create or edit. Tag selection SHALL NOT expand existing task, log, or organization access.

#### Scenario: An App user searches and selects enabled tags
- **WHEN** an authorized user opens a task or log form and searches the tag picker
- **THEN** the picker returns matching enabled tags and allows multiple selections without tag-management actions

#### Scenario: A non-Founder opens My Page
- **WHEN** a Department Manager, Team Leader, or Employee opens the App My Page
- **THEN** no tag-management button is shown, while enabled tags remain selectable in authorized task and log forms

### Requirement: The Founder manages tags from the App My Page
The system SHALL show a tag-management button on the Android App My Page only to the Founder. The Founder SHALL be able to view tags, create a tag, rename an enabled tag, and deactivate a tag. Tag management SHALL NOT be available in the Web console.

#### Scenario: Founder opens tag management
- **WHEN** the Founder taps the tag-management button on My Page
- **THEN** the App opens the management center and displays tag names, colors, and active status, including inactive tags

#### Scenario: Non-Founder attempts tag management
- **WHEN** a non-Founder attempts to create, rename, list inactive tags, or deactivate a tag
- **THEN** the system rejects the management operation and leaves the catalogue unchanged

### Requirement: Tag names and colors remain consistent
The system SHALL require a name for each tag and enforce global uniqueness among enabled tags. Inactive tags SHALL NOT participate in that uniqueness rule. The system SHALL assign each created tag the least-used color from a fixed palette of ten colors, counting inactive tag rows; ties SHALL use fixed palette order. Users SHALL NOT change a tag's color. Renaming a tag SHALL update its displayed name on all associated records without changing its color.

#### Scenario: Duplicate enabled name is rejected
- **WHEN** the Founder creates or renames a tag to a name already used by an enabled tag
- **THEN** the system rejects the operation and preserves the existing tag data

#### Scenario: Inactive name can be reused
- **WHEN** the Founder creates a tag using a name that belongs only to inactive tags
- **THEN** the system permits the new enabled tag and keeps the old inactive tag and its associations unchanged

#### Scenario: Tag color is assigned and remains fixed
- **WHEN** a tag is created and is later renamed or deactivated
- **THEN** its color is assigned by the system from the fixed palette and remains unchanged

### Requirement: Deactivation preserves historical tag associations
When the Founder deletes a tag, the system SHALL deactivate it without physically deleting the tag or removing existing task and log associations. Deactivation SHALL be irreversible through the App. Inactive tags SHALL NOT be available for new associations.

#### Scenario: Founder confirms tag deactivation
- **WHEN** the Founder confirms deletion after the App explains that it cannot be undone and historical associations remain
- **THEN** the tag becomes inactive, remains stored with its associations, and cannot be re-enabled or newly associated

#### Scenario: User removes an inactive tag from an editable record
- **WHEN** a user removes an inactive tag that was already associated with the current task or log and saves the record
- **THEN** the association is removed and that inactive tag cannot be added to the record again

### Requirement: Task tag edits follow existing task permissions
The system SHALL allow tags to be added or removed only through task creation or an existing task edit path authorized by the task's current workflow and status. An inactive association may remain unchanged, but an inactive tag SHALL NOT be newly associated. Changing only tags on an approved task SHALL NOT trigger reapproval.

#### Scenario: Authorized user creates or edits a task with enabled tags
- **WHEN** a user creates a task or edits a task that is editable under the existing task rules and supplies enabled tag selections
- **THEN** the task is saved with those associations and its existing approval, assignment, and completion behavior is preserved

#### Scenario: Task is not editable under existing rules
- **WHEN** a user attempts to change tags on a completed or cancelled task, or in a read-only subordinate task view
- **THEN** the system rejects the tag change and preserves the task associations

#### Scenario: Approved task receives only tag changes
- **WHEN** an authorized editor changes only the tags of an approved task
- **THEN** the association is updated without starting a new approval round

### Requirement: Log tag edits follow existing log permissions and date window
The system SHALL allow only a log's author to add or remove its tags while that log is editable under the existing three-natural-date business-timezone window, including when the log is submitted. Managers SHALL remain read-only. Inactive existing associations may remain unchanged but SHALL NOT be newly associated.

#### Scenario: Author edits tags on a submitted recent log
- **WHEN** the author edits a submitted log dated today, yesterday, or the day before yesterday
- **THEN** the author may update its tag associations while the log remains submitted

#### Scenario: Manager or author outside the editable window changes tags
- **WHEN** a manager edits a subordinate log or an author changes tags on a frozen log
- **THEN** the system rejects the write and preserves the log and its associations

### Requirement: Task and log details show all associated tag names
The system SHALL show every tag name associated with a task or log in its detail view, including names of inactive historical tags. The detail view SHALL NOT mark an associated tag as inactive. Tag names SHALL inherit the task or log's existing visibility scope.

#### Scenario: User with record access opens detail
- **WHEN** a user opens a task or log detail they are authorized to view
- **THEN** the detail shows all associated tag names, including inactive historical names without an inactive marker

### Requirement: Work-view task bars use the earliest current tag color
Month, week, and day work views SHALL show only a task title on each task bar and SHALL color it using the earliest-added tag among the task's current associations. If that association is removed, the next-earliest current association SHALL determine the color. A task without tags SHALL use neutral gray. Inactive historical associations SHALL retain their tag color.

#### Scenario: Task has multiple associated tags
- **WHEN** a user views a task with multiple current tag associations
- **THEN** the task bar shows only its title and uses the color of the earliest-added current association

#### Scenario: Earliest association is removed
- **WHEN** the earliest-added tag is removed from a task
- **THEN** the task bar uses the color of the next-earliest remaining association, or neutral gray if none remain

#### Scenario: Task retains an inactive tag association
- **WHEN** a task's earliest current association is an inactive tag
- **THEN** its task bar continues to use that tag's stored color

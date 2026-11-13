## ADDED Requirements

### Requirement: Home embeds the personal items calendar below company summaries
The system SHALL show two equal-width company overview cards at the top of Home: Company Important Items and Company Top Ten Tasks. Below those cards, Home SHALL embed the full Personal Daily Items monthly calendar for the signed-in account. The Home header SHALL NOT show a personalized greeting, employee position/organization line, or current-date line. Home SHALL NOT display the previous Personal Daily Items preview card or Daily Greeting card.

#### Scenario: User opens Home
- **WHEN** an authenticated mobile business user opens Home
- **THEN** the two company overview cards appear above the user's personal monthly calendar, which can be reached by scrolling the Home page

#### Scenario: User views an empty personal calendar on Home
- **WHEN** the current account has no items in the displayed month
- **THEN** Home shows the calendar's empty state and its create-item action without a separate navigation step

#### Scenario: User interacts with personal items on Home
- **WHEN** the user selects a date or item in the embedded calendar
- **THEN** the existing date-list, detail, edit, delete, and drag interactions are available directly from Home

### Requirement: Home calendar keeps personal items outside company workflows
The system SHALL keep Personal Daily Items separate from Company Top Ten Tasks, task execution, log records, company work views, and employee operation-audit records.

#### Scenario: User creates a personal item
- **WHEN** the user creates, edits, or deletes a Personal Daily Item
- **THEN** the item is not added to a company task or log, and the change does not produce a Web management-audit or employee App operation-audit entry

### Requirement: Users manage only their own personal daily items
Every authenticated App business role SHALL be able to create, view, edit, and delete personal daily items owned by the current account. Other accounts and Web management users SHALL NOT read or manage those items.

#### Scenario: User views the personal calendar
- **WHEN** an authenticated user opens Home and scrolls to its Personal Daily Items calendar
- **THEN** the app shows only items owned by the current account

#### Scenario: Another user attempts to access an item
- **WHEN** an account other than the item's owner attempts to view or modify it
- **THEN** access is denied and the item remains unchanged

### Requirement: Personal item forms capture memo content and inclusive dates
Each personal item SHALL have a required title, required start date, required end date, optional description, and one selected color from the ten-color tag palette. Start and end dates SHALL be inclusive, and the end date SHALL NOT precede the start date. The user MAY change the selected color while editing.

#### Scenario: User creates an item from a selected calendar day
- **WHEN** the user starts creating an item from a selected date
- **THEN** the form defaults both start and end dates to that day and permits editing the title, date range, optional description, and selected palette color

#### Scenario: User saves an invalid date range
- **WHEN** the end date is earlier than the start date
- **THEN** the app rejects the save and explains that the end date must not precede the start date

### Requirement: Personal monthly calendar displays colored date-range bars
The personal calendar SHALL display the owner's items as colored bars across every date in their inclusive range, continuing across week rows. Each bar SHALL use the item's selected color. The calendar SHALL NOT display task completion, overdue, approval, or tag state for personal items.

#### Scenario: Item spans multiple dates or weeks
- **WHEN** the owner views a month containing an item whose start and end dates differ
- **THEN** the same colored item bar appears on all covered dates, including both endpoints and week transitions

#### Scenario: User changes an item's color
- **WHEN** the owner saves a different preset color for an item
- **THEN** all visible calendar segments and item cards use the newly selected color

### Requirement: Calendar provides a quick year and month selector
The personal calendar SHALL allow the user to tap its displayed year and month to open a quick selector. The user SHALL be able to move between years, choose a month, and immediately view that month in the personal calendar.

#### Scenario: User jumps to a selected month
- **WHEN** the user taps the calendar's year/month control, changes the year, and selects a month
- **THEN** the month calendar updates to the selected year and month without opening another page

### Requirement: Personal monthly calendar displays official calendar markers
The personal monthly calendar SHALL display the same configured public-holiday, rest-day, and make-up-workday markers and visual treatment as the company monthly work view. Markers SHALL cover every date in the visible calendar grid, including adjacent-month dates. A named holiday SHALL take precedence over a generic rest-day label; ordinary weekends SHALL NOT be marked, and the app/backend SHALL NOT infer dates for years without configured official schedules.

#### Scenario: User views a configured public holiday
- **WHEN** the visible personal calendar grid includes a configured public holiday, rest day, or make-up workday
- **THEN** its date cell shows the same marker as the company month view, including for adjacent-month dates, with a named holiday taking precedence over a generic rest label

#### Scenario: User views an unconfigured year or ordinary weekend
- **WHEN** the visible grid contains an unconfigured date or an ordinary weekend
- **THEN** no holiday or make-up-work marker is shown for that date

#### Scenario: User changes the displayed month
- **WHEN** the user taps the year/month control and selects another year and month
- **THEN** the personal calendar immediately loads that month's items and configured calendar markers

### Requirement: Crowded dates open a complete item list
When a date contains more items than fit in its calendar cell, the calendar SHALL show the visible bars and a “+N项” affordance. Tapping either the date or the “+N项” affordance SHALL open a colored-card list of every owned item whose inclusive date range covers that date.

#### Scenario: User opens a crowded date
- **WHEN** the owner taps a crowded date or its “+N项” affordance
- **THEN** the app lists all items covering that date as cards colored with each item's selected color

#### Scenario: User opens an uncrowded date
- **WHEN** the owner taps a date with one or more items that all fit in the calendar cell
- **THEN** the app opens the same date list and includes all items covering that date

### Requirement: Item bars and date-list cards open a detail popup
Tapping a personal item bar or its date-list card SHALL open a floating detail card instead of navigating to a separate detail page. The detail card SHALL show title, inclusive start/end dates, optional description, and actions to edit or delete.

#### Scenario: User opens an item from either calendar surface
- **WHEN** the owner taps an item bar or a card in a date list
- **THEN** the app opens the item's detail popup with its content and edit/delete actions

#### Scenario: User edits an item from its detail popup
- **WHEN** the owner selects edit and saves valid changes
- **THEN** the app closes the editor and refreshes the embedded Home calendar, date list, and detail state from the updated item

### Requirement: Item deletion requires confirmation
Deleting a personal item SHALL require explicit confirmation. A confirmed deletion SHALL remove it from the owner's embedded Home calendar and date lists; dismissing the confirmation SHALL preserve it.

#### Scenario: Owner confirms deletion
- **WHEN** the owner confirms deletion in the confirmation dialog
- **THEN** the item is absent from subsequent Home calendar and date-list views

#### Scenario: Owner cancels deletion
- **WHEN** the owner dismisses or cancels the deletion confirmation
- **THEN** the item remains unchanged and visible in the owner's views

### Requirement: Dragging an item bar shifts its entire date range
The owner SHALL be able to long-press and drag an item bar to another date. The bar SHALL visibly follow the pointer and settle onto the target date on release. The app SHALL shift both endpoints by the same day delta, preserving the inclusive duration. Resizing SHALL use the edit form.

#### Scenario: Owner moves a multi-day item
- **WHEN** the owner drags the bar from date A to date B
- **THEN** both endpoints move by the same number of days, keeping the original duration, color, title, and description

#### Scenario: Owner needs to change the duration
- **WHEN** the owner needs to shorten or extend an item's date range
- **THEN** the owner edits its start or end date in the item form instead of resizing the bar

### Requirement: Dragging across a calendar edge navigates between months
While an item is being dragged, entering a calendar edge zone SHALL smoothly advance to the adjacent month and keep the drag active. Remaining in the zone SHALL advance at a controlled interval. Leaving the zone or releasing the item SHALL stop edge navigation.

#### Scenario: Owner continues dragging into an adjacent month
- **WHEN** the owner keeps an item over the calendar's left or right edge zone while dragging
- **THEN** the calendar advances month by month and the owner can drop the item on a date in the displayed month

### Requirement: Personal items do not repeat automatically
Each created personal item SHALL remain a single item over its selected date range. The app SHALL NOT generate additional items from weekly, monthly, or other recurrence rules.

#### Scenario: Calendar advances beyond an item range
- **WHEN** the user views a later week or month outside an item's date range
- **THEN** the app does not create another occurrence automatically

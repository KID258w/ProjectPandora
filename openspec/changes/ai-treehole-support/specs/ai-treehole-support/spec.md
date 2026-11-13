## ADDED Requirements

### Requirement: Treehole provides user-selected non-clinical conversation styles
The system SHALL let a mobile user choose a Treehole interaction preference: primarily listen, help clarify the concern, or offer general suggestions. Treehole output SHALL be presented as general AI support, not diagnosis, treatment, or professional psychological counseling.

#### Scenario: User starts a Treehole conversation
- **WHEN** a user starts a Treehole conversation and chooses an interaction preference
- **THEN** the system applies that preference to the conversation and explains the non-clinical scope

#### Scenario: User requests a diagnosis
- **WHEN** a user asks Treehole to diagnose or treat a mental-health condition
- **THEN** the system does not provide a clinical diagnosis or treatment claim and directs the user to appropriate human/professional support

### Requirement: Treehole conversations are private and isolated from work data
The system SHALL allow the owner to create, list, continue, and delete multiple Treehole conversations. Each conversation SHALL use only its own history and user messages; Treehole SHALL NOT read tasks, logs, MBTI results, other Treehole conversations, or Work Assistant conversations. Managers and administrators SHALL have no access to Treehole content.

#### Scenario: User continues a Treehole conversation
- **WHEN** a user resumes one of their Treehole conversations
- **THEN** the system restores only that conversation's history

#### Scenario: User deletes a Treehole conversation
- **WHEN** a user deletes a Treehole conversation
- **THEN** it is removed from the user's conversation list and excluded from later Pandora model context

#### Scenario: Treehole attempts to access a task or log
- **WHEN** a Treehole request is processed
- **THEN** the system sends no task, log, MBTI, or other conversation data to the model

#### Scenario: Manager or administrator requests Treehole content
- **WHEN** a manager or administrator attempts to read another user's Treehole conversation
- **THEN** the system denies access and returns no conversation content

### Requirement: Treehole presents human-help guidance for potential danger
The system SHALL keep a human-help entry point available in Treehole. If content is assessed as potentially indicating immediate danger or self-harm risk, the system SHALL prioritize that entry point and SHALL NOT imply that it can identify all crisis content or replace emergency/professional care.

#### Scenario: User asks for human support
- **WHEN** a user requests help finding a real person or professional resource
- **THEN** the system presents the configured and release-verified human-help options

#### Scenario: Potential immediate danger is detected
- **WHEN** the system assesses a message as potentially indicating immediate danger
- **THEN** the system prominently presents human-help guidance before ordinary suggestions and does not claim to provide crisis intervention

### Requirement: Treehole requires consent and safe model handling
Treehole SHALL use the server-side AI gateway and the user's required data-processing confirmation. Model credentials SHALL NOT be embedded in the client. Model failure SHALL be surfaced honestly without fabricated support content.

#### Scenario: User has not confirmed AI processing
- **WHEN** the user attempts to send their first Treehole message without required confirmation
- **THEN** the system presents the data-processing notice and sends no message content until confirmed

#### Scenario: AI service fails during Treehole use
- **WHEN** the model service fails or times out
- **THEN** the system displays a clear error and does not fabricate a response

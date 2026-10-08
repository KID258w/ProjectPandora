## ADDED Requirements

### Requirement: Mobile users access a purpose-based AI Map
The system SHALL provide an AI Map tab to authenticated mobile business users with separate entry cards for Work Assistant, MBTI exploration, and emotional Treehole support. The Web administrator console SHALL NOT expose AI conversations or results.

#### Scenario: User enters AI Map
- **WHEN** an authenticated mobile business user opens AI Map
- **THEN** the system shows separate feature choices rather than opening an undifferentiated chat

#### Scenario: Web administrator attempts to access AI data
- **WHEN** a System Administrator requests an AI conversation or result through Web navigation or API
- **THEN** the system denies access and returns no user AI data

### Requirement: Work Assistant supports bounded work scenarios
The system SHALL support task consultation, editable daily-log draft generation, and recent-work summary. Summary SHALL default to the current week. Task and log context SHALL be selected by the current user; the system SHALL NOT automatically read all of the user's business data.

#### Scenario: User consults about a selected task
- **WHEN** a user explicitly selects one of their own tasks and asks for work guidance
- **THEN** the system sends only that task's allowed context and the current conversation to the model and returns advisory text

#### Scenario: User requests a log draft
- **WHEN** a user provides work notes and requests organization into a log draft
- **THEN** the system returns editable text that the user may copy to the log editor, without saving or submitting a log

#### Scenario: User requests a recent-work summary
- **WHEN** a user requests a summary without changing the date range and selects their own task/log context
- **THEN** the system uses the current week and only the selected context to generate a text summary

### Requirement: AI requests require informed consent and minimal selected context
Before a user's first model request, the system SHALL explain that user input and selected task/log content may be sent to the model service and SHALL obtain confirmation. Without confirmation, the system SHALL NOT send content. Work Assistant SHALL access only the current user's explicitly selected data and SHALL send only the minimum allowed fields required for the request.

#### Scenario: User has not confirmed data processing
- **WHEN** the user attempts to send a model request without a prior confirmation
- **THEN** the system displays the data-processing notice and sends no request until confirmation

#### Scenario: User asks a question without selecting business context
- **WHEN** the user sends a general work question without selecting a task or log
- **THEN** the system uses only the message and current conversation history and does not query task or log repositories

#### Scenario: User attempts to select another person's data
- **WHEN** the user attempts to include a subordinate's task or log in Work Assistant context
- **THEN** the system rejects the selection and sends none of that person's data

### Requirement: Work Assistant conversations are independent and user-controlled
The system SHALL allow a user to create, list, continue, and delete multiple Work Assistant conversations. Each conversation SHALL retain its own history as context; new conversations SHALL NOT inherit other conversation or feature data. Only the owner SHALL access the conversation.

#### Scenario: User starts a new conversation
- **WHEN** a user creates a new Work Assistant conversation
- **THEN** it begins without messages or context from other conversations, MBTI results, or Treehole content

#### Scenario: User resumes an existing conversation
- **WHEN** a user opens one of their existing conversations
- **THEN** the system restores only that conversation's own history

#### Scenario: User deletes a conversation
- **WHEN** a user deletes their conversation
- **THEN** it is removed from their list and excluded from subsequent Pandora model context

### Requirement: AI output never mutates formal business records
The system SHALL present Work Assistant output as AI-generated advice or draft content and SHALL NOT automatically create or alter tasks, progress, acceptance results, or submitted logs. Model credentials SHALL remain server-side; model failures SHALL be reported without fabricating a response.

#### Scenario: User receives generated work guidance
- **WHEN** the model returns a work suggestion or summary
- **THEN** the system labels it as AI-generated and leaves formal business records unchanged

#### Scenario: Model service fails
- **WHEN** the model service times out or returns an error
- **THEN** the system shows a clear failure state, does not fabricate a response, and does not duplicate the failed message

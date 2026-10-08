## ADDED Requirements

### Requirement: Users complete a personal MBTI exploration flow
The system SHALL allow an authenticated mobile business user to start an MBTI self-exploration questionnaire, see answering progress, return to change earlier answers, and submit only after required answers are complete. The questionnaire content and scoring SHALL use the approved version selected before implementation.

#### Scenario: User completes the questionnaire
- **WHEN** a user answers all required questions and submits the assessment
- **THEN** the system calculates and displays the result using the approved questionnaire version

#### Scenario: User revises an answer before submission
- **WHEN** a user returns to a prior question and changes an answer
- **THEN** the updated answer is used in the final result calculation

#### Scenario: User submits an incomplete assessment
- **WHEN** a user attempts to submit while required answers are missing
- **THEN** the system identifies incomplete questions and does not create a new result

### Requirement: MBTI results are private and for self-exploration only
The system SHALL present MBTI results as non-diagnostic self-exploration content and SHALL NOT use them for hiring, performance evaluation, task assignment, or management decisions. Only the owner SHALL be able to read the result; managers and administrators SHALL have no result-viewing interface or API access.

#### Scenario: User views their result
- **WHEN** a user opens their latest completed MBTI result
- **THEN** the system shows self-reflection and communication-preference content with a clear non-diagnostic limitation

#### Scenario: Manager or administrator attempts to read a result
- **WHEN** a manager or administrator requests another user's MBTI result
- **THEN** the system denies access and returns no result data

### Requirement: Only the latest completed MBTI result is retained for display
The system SHALL display and retain only the user's latest completed result. A new completed assessment SHALL replace the previous result; an incomplete or failed reassessment SHALL NOT replace it. MBTI answers/results SHALL NOT be added automatically to Work Assistant or Treehole context.

#### Scenario: User completes a reassessment
- **WHEN** a user completes a new assessment after having a prior result
- **THEN** the new result becomes the only result available in the user's result view

#### Scenario: User abandons a reassessment
- **WHEN** a user leaves a reassessment before completing it
- **THEN** the existing latest completed result remains unchanged

#### Scenario: Work Assistant starts a new conversation
- **WHEN** a user begins a Work Assistant or Treehole conversation
- **THEN** the system does not attach MBTI answers or results unless a future separately approved requirement explicitly allows it

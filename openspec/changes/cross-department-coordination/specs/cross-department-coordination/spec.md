## ADDED Requirements

### Requirement: Department Managers initiate and process cross-department requests
The system SHALL allow a Department Manager to send a collaboration request to another Department Manager. Only the requesting and target Department Managers SHALL access that request. The Founder, Team Leaders, Employees, and System Administrator SHALL NOT view or process cross-department requests.

#### Scenario: Department Manager sends a request
- **WHEN** a Department Manager submits a request with required target department and work details
- **THEN** the system creates a request in awaiting-target-acceptance state and notifies the target Department Manager

#### Scenario: Target Department Manager accepts
- **WHEN** the target Department Manager accepts a pending request
- **THEN** the request enters in-progress and the work appears in that manager's cross-department work list for execution within the target department

#### Scenario: Target Department Manager rejects
- **WHEN** the target Department Manager rejects a pending request with a reason
- **THEN** the request enters rejected state and the requesting manager can view the reason

#### Scenario: Non-participant attempts to view the request
- **WHEN** a Founder or another non-participating role requests the request through UI or API
- **THEN** the system denies access and returns no request details

### Requirement: Cross-department work is executed within the target department
After acceptance, the target Department Manager SHALL organize execution using the normal department hierarchy and SHALL submit the resulting work to the requesting Department Manager for acceptance. The requesting manager SHALL NOT access the target department's subordinate task details.

#### Scenario: Target department submits a result
- **WHEN** the target Department Manager submits the result of accepted cross-department work
- **THEN** the request enters awaiting-requester-acceptance and the requesting manager is notified

#### Scenario: Requesting manager accepts the result
- **WHEN** the requesting Department Manager accepts the submitted result
- **THEN** the request enters closed state

#### Scenario: Requesting manager returns the result
- **WHEN** the requesting Department Manager returns the result with a reason
- **THEN** the request returns to in-progress, and the target manager can view the reason and resubmit

#### Scenario: Requester attempts to inspect target employee tasks
- **WHEN** the requesting manager requests task details created inside the target department
- **THEN** the system denies access while continuing to expose the request status and submitted result

### Requirement: Pending requests have controlled withdrawal and immutable accepted scope
The system SHALL prevent editing a sent request. Before acceptance, only the requesting manager MAY withdraw it and MUST provide a reason. After acceptance, MVP SHALL NOT permit either party to cancel or withdraw it; additional scope requires a new request.

#### Scenario: Requester withdraws a pending request
- **WHEN** the requesting manager withdraws a request awaiting acceptance with a reason
- **THEN** the request enters withdrawn state, retains the reason, and notifies the target manager

#### Scenario: Requester withdraws without a reason
- **WHEN** the requesting manager attempts to withdraw a pending request without a reason
- **THEN** the system rejects the operation and leaves the request unchanged

#### Scenario: A party attempts to cancel accepted work
- **WHEN** either department manager attempts to cancel or withdraw an accepted request
- **THEN** the system rejects the operation and preserves the accepted request state

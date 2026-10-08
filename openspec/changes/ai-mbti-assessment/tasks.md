## 1. Assessment content and data

- [ ] 1.1 Confirm and document questionnaire source/permission, question set, question count, scoring rules, and result copy before implementation.
- [ ] 1.2 Add versioned questionnaire definition and owner-scoped latest-result persistence.
- [ ] 1.3 Implement answer validation, result calculation, and atomic replacement only after a completed reassessment.
- [ ] 1.4 Enforce owner-only result APIs and exclude answers/results from manager, administrator, and other AI-context APIs.

## 2. Android assessment experience

- [ ] 2.1 Build MBTI feature entry, purpose/limitation notice, question progress, answer selection, and back-to-edit navigation.
- [ ] 2.2 Build result page with approved self-reflection content and non-diagnostic limitation.
- [ ] 2.3 Build retest flow and show only the latest completed result.

## 3. Verification

- [ ] 3.1 Test required-answer validation, scoring against approved test cases, result version, and incomplete-retest preservation.
- [ ] 3.2 Test owner-only access and verify no manager/admin or cross-feature AI context can read the result.
- [ ] 3.3 Verify UI and backend do not expose result for hiring, performance, or task-assignment workflows.

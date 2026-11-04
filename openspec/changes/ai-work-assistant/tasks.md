## 1. AI conversation and gateway foundation

- [ ] 1.1 Add owner-scoped AI conversation/message records with feature type, creation/deletion state, and context separation.
- [ ] 1.2 Implement authenticated create/list/resume/delete conversation APIs with owner-only access.
- [ ] 1.3 Implement a server-side DeepSeek gateway with credentials outside mobile/Web clients, timeout/error handling, and safe response persistence.
- [ ] 1.4 Implement data-processing notice/confirmation and block model requests until the required user confirmation is recorded.
- [ ] 1.5 Implement a context builder that accepts only current-user selected task/log fields and current-conversation history.

## 2. Work Assistant behavior

- [ ] 2.1 Implement task-consultation prompt flow with explicit own-task selection and no business write tools.
- [ ] 2.2 Implement log-draft generation and manual transfer to the log editor without automatic save/submit.
- [ ] 2.3 Implement recent-work summary with current-week default and user-selected task/log/date context.
- [ ] 2.4 Mark generated responses as AI output and ensure no task/progress/acceptance/submitted-log mutation.

## 3. Android experience

- [ ] 3.1 Add AI Map tab and separate feature cards; keep administrator Web navigation free of AI features.
- [ ] 3.2 Build Work Assistant scenario selection, context picker, consent notice, conversation list, and active chat.
- [ ] 3.3 Build log-draft review and explicit handoff to the daily-log editor.
- [ ] 3.4 Display loading, timeout, failure, and retry states without duplicate messages.

## 4. Verification

- [ ] 4.1 Test consent gating, selected-context minimization, owner-only access, and rejection of subordinate data.
- [ ] 4.2 Test conversation isolation, resume/delete behavior, and no cross-feature memory.
- [ ] 4.3 Test model failure handling and verify AI actions cannot write tasks, progress, acceptance, or submitted logs.
- [ ] 4.4 Verify OpenAPI contracts and run an end-to-end task consultation and log-draft scenario.

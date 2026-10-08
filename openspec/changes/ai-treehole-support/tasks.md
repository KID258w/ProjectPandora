## 1. Isolated Treehole support

- [ ] 1.1 Add Treehole conversation type and enforce owner-only access separate from Work Assistant conversations.
- [ ] 1.2 Implement listen/clarify/suggest preference selection and corresponding non-clinical system instructions.
- [ ] 1.3 Enforce request context allowlist containing only current user messages and the active Treehole conversation history.
- [ ] 1.4 Add human-help resource configuration and verify target-region availability, wording, and release readiness.
- [ ] 1.5 Implement conservative potential-danger handling that elevates help guidance without claiming complete detection or crisis treatment.

## 2. Android Treehole experience

- [ ] 2.1 Build Treehole entry, scope explanation, interaction-style selection, conversation list, and chat screen.
- [ ] 2.2 Add persistent human-help entry point and prioritized presentation for potential-danger states.
- [ ] 2.3 Build owner-controlled conversation deletion and clear model-failure presentation.

## 3. Verification and safety review

- [ ] 3.1 Test strict separation from tasks, logs, MBTI, other Treehole chats, and Work Assistant chats.
- [ ] 3.2 Test manager/admin denial, consent gating, deletion, and server-side credential boundaries.
- [ ] 3.3 Review sample outputs for non-clinical language, no diagnostic labels, and human-help routing behavior.
- [ ] 3.4 Verify release checklist blocks enabling Treehole until the configured human-help information has been checked.

# Coding standards

These initial standards support review. Add language-specific conventions when
the Android toolkit and build configuration are chosen.

- Preserve the behavior and boundaries in the originating specification. Raise
  contradictions rather than resolving product choices implicitly.
- Keep each ticket's change coherent and independently demonstrable. Prefer
  the smallest design that satisfies its accepted behavior.
- Test observable behavior at agreed boundaries. Keep live network side effects
  separate from ordinary automated checks.
- Keep wallet keys and signing material local to their owner. Logs, analytics,
  screenshots, commits and public payloads must not expose secrets.
- Treat compatibility with existing Saito signatures, wallet exports and
  Red Square transactions as an explicit integration requirement.
- Preserve user identity and recoverable work through updates and connection
  failures. Explain unavoidable recovery steps in product language.
- Use plain punctuation in product text. Do not use em dashes in app strings.
- Review responsiveness on the target device. Treat mocks and emulator results
  as evidence with limits rather than proof of physical-device performance.

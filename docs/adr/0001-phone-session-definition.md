# Phone session: unlock → non-interactive, with a 30s merge window

A session starts at actual unlock (`ACTION_USER_PRESENT`, cross-checked with `KeyguardManager.isKeyguardLocked() == false`) and ends when the device goes non-interactive (`ACTION_SCREEN_OFF`; always-on display counts as off). A screen-on that is never unlocked is a peek, not a session. A re-unlock within 30 seconds of screen-off resumes the same session — so screen timeouts, pocket relocks, and accidental power-button presses don't re-trigger the Intent Prompt or inflate Awareness. Alternatives rejected: launcher-foreground sessions (fires behind the keyguard and on every Home press) and screen-on sessions (counts notification peeks). Full mechanism survey: `docs/research/phone-session.md` on branch `research/phone-session`, resolved in issue #33.

## Degraded modes

- Lock screen set to "None": `USER_PRESENT` fires on every wake, so every wake starts a session — no stronger signal exists there.
- UsageStats (`SCREEN_INTERACTIVE` / `KEYGUARD_HIDDEN`) backfill repairs sessions missed during process death only when usage access is already granted for other features; it is never a prerequisite for session detection.
- After a process kill mid-session, reconstruct state on restart by polling `PowerManager.isInteractive()` + `isKeyguardLocked()` rather than assuming the session closed.

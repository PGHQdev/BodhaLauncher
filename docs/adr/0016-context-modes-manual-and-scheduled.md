# Context modes: named Home arrangements, manual or scheduled

A context mode is a named set of Home pins with an optional time window, reusing the `ScheduleWindow` already in the engine for Open Check's schedule trigger. It switches **Home's pinned actions and nothing else**. Open Check already owns time-based friction through its own `Schedule` mode, so per-mode rules would make one behaviour expressible in two places — the mistake ADR 0012 just cleaned up by retiring `DuringFocus`. Notification suppression belongs to Focus, and Search has no context tier (ADR 0014).

#14's inference model — time, calendar, location, connected Bluetooth, media session, recent usage — is not built. Three landed decisions already refuse to infer what the user did not state (ADR 0007's deterministic-first stance, ADR 0013's rejection of implicit intent signals, ADR 0014's dropped contextual and inferred ranking tiers), and building an inference engine here would make those look arbitrary. The failure mode is also the worst available: a wrong context means a wrong Home, on a surface seen fifty times a day, which is where #28's launcher-reliability and AI-overreach risks multiply. Half of #14's modes cannot switch themselves regardless — Commute and Travel need location or activity recognition, connected-Bluetooth needs `BLUETOOTH_CONNECT`, media state needs a session listener — so the full list would ship mostly as decoration. Inference remains available as the "advanced context suggestions" ADR 0005 already sells as Pro, layered on this without rework if real use justifies it.

Nothing ships pre-made. Today's Home is the default arrangement: always present, unnamed, unschedulable. Every mode is one the user built — a name, a set of pins, an optional window. Prefilled starters were rejected because prefilled pins are guesses about someone's job and someone's evening, the presumption the empty Home avoids.

Modes are ordered and the first matching window wins; windows will overlap and something has to break the tie deterministically. A manual switch holds **until the next window boundary**, so schedules resume without the user undoing anything and the expiry is a moment they can predict. Holding until the 4am day boundary was rejected (one manual switch would silently disable every schedule for a day) and so was holding indefinitely (the schedules quietly stop working with no sign of why).

A mode label appears on Home only while a non-default mode is active, so the empty Home of ADR 0011 stays empty in the common case and the user is never switched without being told. Mode management lives in long-press → edit mode, beside Edit Home and Settings, where Home arrangement is already edited.

Free and uncapped: ADR 0005 puts basic context modes in the free tier, and a mode cap would be a paywall this decision has not earned.

This is a much smaller feature than #14 describes, and a mode nobody creates may prove to be a mode nobody wanted. That is a cheap thing to discover post-launch, and the reason to build this version rather than the inference engine.

Resolved in issue #94.

# Focus session: three fields, allowed-list checks, no pause

A Focus session carries an activity label, a duration, and a set of allowed apps. #9's optional linked task is dropped — there is no task system, and building one to satisfy an optional field is scope invented from a spec line. Its optional end condition ("such as next meeting") is dropped too: it needs the calendar provider, and an end condition that isn't a duration is the scheduling axis ADR 0005 already puts behind Pro. The free tier's "single ad-hoc Focus session" means one running at a time, not one per day — "ad-hoc" contrasts with *scheduled*, and paywalling the second session of a Tuesday is the resentment moment ADR 0005 exists to avoid.

While a session runs, opening any app not on its allowed list fires an Open Check. The per-session list is strictly more expressive than a persistent per-app mode, so `OpenCheckMode.DuringFocus` is retired — a small store migration on a value that has never been reachable (#77 left it inert). `OpenCheckContext.focusActive` stays and is now genuinely fed. A free user therefore gets unlimited checked apps during a session without consuming any of the three free Open Check rules; that is intended, because it is time-boxed and per-session rather than persistent configuration.

There is no pause. End is already there and starting again is three fields, where pause would add a third lifecycle state that has to persist across process death, decide whether the end instant shifts, and be explained afterwards. The session stores its end instant and resumes against wall-clock across process death and reboot, exactly as `TimedSession` does; an end that passed while the process was dead is reported late rather than lost. OEM background-killing is a named risk (#28), so ending on process death was rejected — it would cancel sessions silently on the devices that kill most aggressively.

A session ends silently. Root reverts to Home (ADR 0011), and the end moment waits until the user next lands on root: what they focused on, how long, and one neutral line for how often they reached elsewhere, with extend and done. Nothing interrupts a phone lying face-down, which is the state the session exists to produce. A notification at the moment of ending was rejected: it makes Bodha the interruption.

The session records each check it fired and whether the user proceeded — the raw material Awareness needs, at no extra cost since checks already log events. The end moment shows one line, no ratio and no praise; #9's "no streak or focus score" holds. How Awareness presents the same data is its own decision.

Called a **Focus session**, qualified the way "timed session" already is; bare "session" stays reserved for the phone session (ADR 0001). Code gets `FocusSession` beside `TimedSession`.

Resolved in issue #86.

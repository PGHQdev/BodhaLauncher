# Focus records live under Reflections

The durable record a Focus session leaves (#169) — label, start and end instants, reach and proceed counts — prunes under `RetentionCategory.Reflections`. No new enum member: #146's privacy dashboard and #148's delete-a-category control cover the store the day it exists, exactly as they cover Open Check intentions.

The deciding field is the label. It is user-written text — "thesis chapter", "call with mum" — which is the same property that routed Open Check intentions to Reflections rather than the event log: what the user wrote about their own attention is reflective material, not telemetry. Filing it under `EventLog` would put a year's window on it and a delete control that also wipes unrelated diagnostics; filing it under `RawUsageEvents` would silently drop a user's own words after 30 days. Reflections has no default window — kept until the user deletes it — which is the only honest default for text the user authored, and it means Pro's Focus history (#11's Awareness views) is bounded by the user's choice alone.

The timestamps and counts ride with the label rather than splitting into a second category: one session, one record, one deletion. The record carries no app identity — counts, never names — so nothing in it exceeds what the intentions already established for the category (ADR 0009 is about transmission and is untouched; nothing leaves the device).

The existing daily worker prunes Reflections wherever a cutoff exists, so the store needs only the same wiring the intent records have; by default there is no cutoff and nothing is cut.

Resolved for issue #169.

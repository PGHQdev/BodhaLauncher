# Session records live under RawUsageEvents

The durable session records Awareness's four views read (#171) — id, start, end, day key — prune under `RetentionCategory.RawUsageEvents`, the same category ADR 0013 chose for the launch log. No new enum member: #146's privacy dashboard and #148's delete-a-category control cover the store the day it exists.

The record is pure device signal — timestamps the engine derived from unlocks and screen-offs (ADR 0001). It carries no user-written text, which is what routes intentions to `Reflections`; no app identity; nothing a notification held. Of the existing categories only `RawUsageEvents` describes it, and the launch log it will be joined against on every Awareness view already sits there — two stores one view reads should age out together, or the view lies about one of them.

The consequence the category sets is history: 30 days by default, user-adjustable, where `EventLog`'s 365 would have kept a year. Pro's Awareness history is therefore the retention window, not the entitlement window — exactly the shape ADR 0013 already settled ("retention governs what exists; entitlement governs what renders"), with free rendering 7 days of it (ADR 0005). A year of per-session rows was rejected: it is a year of unlock-by-unlock behaviour on disk that no shipped view asks for, and the aggregation step (#19) is where long history is supposed to live once metrics need it.

Raw usage rolls up into `AggregatedUsage` before deletion; session records have no rollup yet, so until an aggregate needs them the retention worker's cut is a plain delete at the same aligned 4am boundary.

Resolved for issue #171.

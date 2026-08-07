package com.bodhalauncher.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One phone session as Awareness reads it (#171, ADR 0028): the engine's
 * session (ADR 0001) made durable. A null end is a session still running —
 * rendered as running, never as a zero-length one.
 */
data class SessionRecord(
    val id: Long,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    /**
     * The day a session belongs to is the day it started (ADR 0003). Defaulted
     * from the start, but a store that stamped the key at write passes its own —
     * one source of truth per record, never two readings of the boundary.
     */
    val day: LocalDate = dayKey(start),
)

/**
 * The three signals that make a session intentional (ADR 0013): the user
 * answered the Intent Prompt, wrote an Open Check intention (#76), or ran a
 * Focus session. Everything else is unclassified — never "unintentional",
 * because the phone does not know.
 *
 * Named once, as the events that state them. [computeMetrics] reads this set
 * directly and Awareness's view reads the same three sources as [IntentSignal]s,
 * so the definition is one thing rather than two that happen to agree.
 */
val INTENT_SIGNAL_EVENTS: Set<EventType> = setOf(
    EventType.IntentPromptAnswered,
    EventType.OpenCheckIntentionWritten,
    EventType.FocusStarted,
)

/**
 * One stated intent, at the moment it was stated (#172).
 *
 * [session] is the session the record named, and it decides on its own where one
 * is named — a prompt answer carries the id it was asked under. An Open Check
 * intention and a Focus record carry none, so the span they fell inside is what
 * attributes them, which is the whole of "attributed to the session they
 * happened in".
 *
 * [text] is the user's own words where the signal carried any — a typed Open
 * Check intention, a Focus session's label, a prompt answer someone wrote into.
 * Null is a signal stated without words, which is a real answer and not a
 * missing one: a category picked from the prompt states an intent and spells
 * out nothing.
 */
data class IntentSignal(
    val at: LocalDateTime,
    val session: Long? = null,
    val text: String? = null,
)

/** A session with its classification — intent as an attribute, never a fifth view (ADR 0013). */
data class AwarenessSession(val record: SessionRecord, val intentional: Boolean)

/**
 * Whether a timestamp fell inside this session. An open record has no upper
 * bound: the session still running is the one anything after its start happened
 * in.
 */
private fun SessionRecord.holds(at: LocalDateTime): Boolean =
    !at.isBefore(start) && (end == null || at.isBefore(end))

private fun IntentSignal.belongsTo(record: SessionRecord): Boolean =
    if (session != null) session == record.id else record.holds(at)

/**
 * The day's sessions in time order, each classified (#172). One rule, applied to
 * whatever signals the caller could read: a source it could not reach simply
 * contributes nothing, which classifies a session as unclassified rather than
 * as a falsehood.
 */
fun resolveAwarenessSessions(
    records: List<SessionRecord>,
    signals: List<IntentSignal>,
): List<AwarenessSession> = records
    .sortedBy { it.start }
    .map { record -> AwarenessSession(record, signals.any { it.belongsTo(record) }) }

/**
 * Awareness's Today view (#171): a count, or the absence said in words.
 * [None] exists because 0 must never stand in for an unknown — an empty read
 * and a failed one are the caller's to tell apart before resolving.
 */
sealed interface AwarenessToday {
    data object None : AwarenessToday
    data class Sessions(val finished: Int, val running: Boolean) : AwarenessToday
}

fun resolveAwarenessToday(records: List<SessionRecord>, now: LocalDateTime): AwarenessToday {
    val today = dayKey(now)
    val finished = records.count { it.end != null && it.day == today }
    val running = records.any { it.end == null }
    return if (finished == 0 && !running) AwarenessToday.None
    else AwarenessToday.Sessions(finished = finished, running = running)
}

/**
 * The one rendered line. Bare counts only — no delta, no direction word,
 * no second ink, no ranking (ADR 0013).
 */
fun awarenessTodayLine(view: AwarenessToday): String = when (view) {
    AwarenessToday.None -> "No sessions yet today"
    is AwarenessToday.Sessions -> when {
        view.finished == 0 -> "A session is running now"
        view.running -> "${sessionsCount(view.finished)} today · one running now"
        else -> "${sessionsCount(view.finished)} today"
    }
}

/**
 * A session row's line: when it started, and how long it ran (#172).
 *
 * Nothing is marked short. The spec names short sessions, but no threshold
 * exists anywhere and inventing one here would invent a judgement (ADR 0013).
 */
fun awarenessSessionLine(
    session: AwarenessSession,
    /**
     * The chosen clock (#141): a session's start is a wall-clock time Bodha
     * writes, so it is spelled the way Home's clock spells it. The default is
     * what this line already read, so callers that have no preference to hand
     * are unchanged.
     */
    clock: ClockFormat = ClockFormat.TwentyFourHour,
): String {
    val started = formatClock(session.record.start.toLocalTime(), clock)
    val end = session.record.end ?: return "$started · running now"
    val millis = Duration.between(session.record.start, end).toMillis()
    return "$started · ${if (millis < 60_000) "under a minute" else spanPhrase(millis)}"
}

/** The classification as a word, in one ink and with no valence colour (ADR 0013). */
fun awarenessIntentWord(intentional: Boolean): String =
    if (intentional) "Intentional" else "Unclassified"

private fun sessionsCount(finished: Int): String =
    if (finished == 1) "1 session" else "$finished sessions"

/**
 * One launch Bodha mediated (#173, ADR 0013): what was opened, when, and the
 * session it happened in. App identity and a timestamp, and nothing else — the
 * event log could carry neither, which is why this store exists at all.
 */
data class LaunchRecord(
    val appId: String,
    val at: LocalDateTime,
    /** Null for a launch with no session open: recorded, and attributed to none. */
    val session: Long? = null,
)

/**
 * Awareness's Session view (#173): one session opened up — what was launched in
 * it and in what order, the Open Checks it raised, whether a repeated open was
 * noticed, and the words the user stated if there were any.
 */
data class SessionDetail(
    val session: AwarenessSession,
    val launches: List<LaunchRecord>,
    val checks: Int,
    val repeatedOpen: Boolean,
    val statement: String?,
)

/**
 * Resolves one session's view from the three stores that know about it (#173).
 *
 * A launch belongs to the session **it named** and to no other: the id was read
 * at the moment of the launch, which is a stronger attribution than any span,
 * and it is what keeps a launch made outside a session out of every session.
 * Events carry no session — the event log is a type and a timestamp (ADR 0009) —
 * so the span they fell inside is what attributes them, the same rule [IntentSignal]
 * already takes when it names none.
 */
fun resolveSessionDetail(
    session: AwarenessSession,
    launches: List<LaunchRecord>,
    events: List<LoggedEvent>,
    signals: List<IntentSignal>,
): SessionDetail {
    val record = session.record
    val inSession = events.filter { record.holds(it.at) }
    return SessionDetail(
        session = session,
        launches = launches.filter { it.session == record.id }.sortedBy { it.at },
        checks = inSession.count { it.type == EventType.OpenCheckDisplayed },
        repeatedOpen = inSession.any { it.type == EventType.RepeatedOpenDetected },
        // The first signal that carried words: a session may hold several, and
        // the one that spoke is the one worth reading back.
        statement = signals.filter { it.belongsTo(record) }.firstNotNullOfOrNull { it.text },
    )
}

/**
 * A launch's own line: the time it happened, spelled the way the clock is (#141).
 * The app's name is the row's title and comes from the catalog, never from here —
 * the record holds an id, and an id is not a name.
 */
fun launchTimeLine(
    launch: LaunchRecord,
    clock: ClockFormat = ClockFormat.TwentyFourHour,
): String = formatClock(launch.at.toLocalTime(), clock)

/**
 * What the Session view says beneath its rows (#173): bare statements of what
 * happened, in the order they are worth reading — never a count of nothing, and
 * never a word carrying a verdict (ADR 0013).
 *
 * The absence of launches is a line here rather than an invented row, which is
 * what keeps "the view never invents a row" true for a session that raised a
 * check and opened nothing.
 */
fun sessionDetailNotes(detail: SessionDetail): List<String> = buildList {
    if (detail.launches.isEmpty()) add("Nothing was opened in this session")
    if (detail.checks > 0) add("${plural(detail.checks.toLong(), "Open Check")} fired")
    if (detail.repeatedOpen) add("A repeated open was noticed")
}

/**
 * One day of one app's opens (#174). The day is [dayKey]'s — the 4am boundary
 * (ADR 0003), the same one a session belongs to its start by, so a late-night
 * open and the session it happened in file under the same heading.
 *
 * The key is computed here, at read, where [SessionRecord.day] is stamped at
 * write. That asymmetry is deliberate and worth naming rather than
 * rediscovering: `launch_record` has no day column, and adding one would file
 * every record already written under epoch day 0, because Room requires a
 * NOT NULL added column to carry a default. There is nothing to backfill from
 * that a read cannot compute for free.
 */
data class AppDay(val day: LocalDate, val opens: List<LaunchRecord>)

/**
 * Awareness's App view (#174, ADR 0013): one app — when it was opened, how
 * often over the records it was handed, and how many sessions those opens fell
 * in. Sessions are a count rather than rows, because a list of sessions here
 * would be the Session view rendered twice.
 *
 * [name] is the catalog's label where the app is still installed and the
 * recorded id where it is not; [installed] is which of the two happened. An
 * uninstalled app keeps its records and its recorded identity — dropping it
 * would edit the record to match what the phone currently holds, which is the
 * one thing a neutral record must not do (#11).
 *
 * [foreground] is a **named state and never a number standing alone** (#175):
 * the field exists whether or not anything could measure it, and the one thing
 * it can never be is a 0 filling in for an unknown. Its default is the state a
 * phone without usage access is permanently in, so a caller that reads no usage
 * at all gets the true answer rather than a hopeful one.
 */
data class AppOpens(
    val appId: String,
    val name: String,
    val installed: Boolean,
    val days: List<AppDay>,
    val opens: Int,
    val sessions: Int,
    val foreground: AwarenessDuration =
        AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
)

/**
 * Resolves one app's view from a list of launches the caller has already
 * prepared (#174).
 *
 * Three rules keep this total whatever the caller managed to read, and keep the
 * tickets after this one out of it. It **reads nothing** — every store is the
 * caller's. It **filters by [appId] itself**, so handing it the whole log and
 * handing it one app's records give the same answer. Its counts are **folded
 * from the records it groups**, never counted separately, so the line under the
 * title cannot disagree with the rows beneath it.
 *
 * What changes after this ticket is what arrives on [launches] and not what
 * happens to it: a second source of opens merged in (#175), the entitlement
 * window clamped (#177), excluded records filtered out (#178). None of them
 * needs a signature here.
 *
 * Newest first, both within a day and across days: the list is unbounded in the
 * past, and the end worth reading is the near one.
 *
 * [label] is the app's name where the catalog still has one to give, and null
 * where it does not — an input rather than a lookup, so "named as uninstalled"
 * is a rule this module states and a unit test can hold it to.
 *
 * [foreground] is passed through untouched, and the caller owes it one promise
 * that this function cannot check: it must cover **the same window [launches]
 * does** (#175). A span read over a month sitting above opens clamped to a week
 * would be two answers to two different questions printed as one.
 */
fun resolveAppOpens(
    appId: String,
    label: String?,
    launches: List<LaunchRecord>,
    foreground: AwarenessDuration =
        AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess),
): AppOpens {
    val mine = launches.filter { it.appId == appId }.sortedByDescending { it.at }
    val days = mine.groupBy { dayKey(it.at) }
        .map { (day, opens) -> AppDay(day, opens) }
        .sortedByDescending { it.day }
    return AppOpens(
        appId = appId,
        name = label ?: appId,
        installed = label != null,
        days = days,
        opens = mine.size,
        // Distinct, because a session an app was opened in three times is one
        // session it appeared in. A launch that named none joins none.
        sessions = mine.mapNotNull { it.session }.distinct().size,
        foreground = foreground,
    )
}

/**
 * The line under the App view's title (#174): bare counts of what the view is
 * about to draw, and the absences said in words.
 *
 * Never a count of nothing — no "0 sessions" for an app only ever opened outside
 * a session — which is the rule [sessionDetailNotes] already takes. No delta, no
 * direction word, no ranking against any other app (ADR 0013): this view is
 * shaped the same for an app opened once and an app opened two hundred times,
 * and the counts are the only difference between them.
 */
fun appOpensLine(view: AppOpens): String = buildList {
    if (!view.installed) add("No longer installed")
    if (view.opens == 0) {
        add("No opens recorded")
    } else {
        add(plural(view.opens.toLong(), "open"))
        if (view.sessions > 0) add(plural(view.sessions.toLong(), "session"))
    }
}.joinToString(" · ")

/**
 * A day heading over the opens that fell in it (#174), spelled the way Today
 * spells its date (#141).
 *
 * The date and nothing else. The rows under a heading already say how many opens
 * there were and when each happened, so a count on the heading would restate
 * them — and would put one day's number a scroll above another's, which is a
 * comparison the reader did not ask for even where ADR 0013 permits bare numbers
 * to sit adjacent.
 */
fun appDayLine(day: AppDay, date: DateFormat = DateFormat.WeekdayAndMonth): String =
    formatDate(day.day, date)

/**
 * One moment the front of the phone became this app (#175), read from Android's
 * usage events where usage access is granted.
 *
 * Not a [LaunchRecord], and deliberately so: an app resuming its own next
 * activity produces one of these and is not a launch at all. What survives
 * [resolveForegroundOpens] may become a launch; what arrives here has not
 * earned the name yet.
 */
data class ForegroundEntry(val appId: String, val at: LocalDateTime)

/**
 * How far after a launch Bodha logged the system's own entry for it may fall and
 * still be the same opening (#175).
 *
 * Forward-only, and that direction is a property of the code rather than a
 * guess: Bodha writes the launch record and *then* starts the activity, so the
 * system's `ACTIVITY_RESUMED` is always the later of the two. Ten seconds is a
 * cold start on a slow phone with room to spare; a symmetric window would have
 * swallowed a genuine re-open of an app closed moments earlier.
 */
val SAME_LAUNCH_WINDOW: Duration = Duration.ofSeconds(10)

/**
 * The foreground stream reduced to the moments the front actually changed (#175).
 *
 * An app in use resumes over and over — a new activity, a returning dialog, a
 * rotation — and each of those is one visit continuing, not a second opening.
 * The rule is the smallest one that says so: keep an entry only where the
 * previous entry named a different app.
 *
 * That is why the caller must hand over the **whole, unfiltered** stream. Run
 * over a stream already narrowed to one app, every entry has the same
 * predecessor and exactly one survives, which is the difference between an app
 * showing every opening it had and showing one.
 *
 * It still counts twice where an app resumes its own next activity with another
 * app's resume in between — a share sheet, a photo picker, a custom tab. The
 * front genuinely changed there, and telling that apart needs paused events and
 * a span model nothing else in Bodha wants.
 */
internal fun resolveForegroundOpens(entries: List<ForegroundEntry>): List<ForegroundEntry> {
    val ordered = entries.sortedBy { it.at }
    return ordered.filterIndexed { index, entry ->
        index == 0 || ordered[index - 1].appId != entry.appId
    }
}

/**
 * One app's opens from both sources at once (#175, ADR 0013): what Bodha
 * mediated, plus what Android saw and Bodha did not — a notification tap, a
 * return through recents, a hand-off from another app.
 *
 * **The order of the four steps is the whole of this function**, and reversing
 * any of the first two is a silent one-row bug rather than a crash:
 *
 * 1. collapse the whole stream, over every app, so a run of one app's own
 *    resumes becomes one entry and a second visit later stays a second entry;
 * 2. *then* filter the survivors to [appId], because collapsing after filtering
 *    leaves exactly one entry no matter how many openings there were;
 * 3. drop any survivor falling inside [SAME_LAUNCH_WINDOW] after a launch
 *    already logged for this app — the two sources describing one opening;
 * 4. carry the rest over as launches with **no session**, and union with what
 *    was logged, oldest first.
 *
 * A launch Bodha did not mediate names no session and joins none. That is not a
 * gap to be filled in later: no session id was read at the moment it happened,
 * and attributing it by the span it fell inside would invent an attribution the
 * launch log's own rule refuses (see [resolveSessionDetail]).
 *
 * Where the two sources disagree the log wins on "Bodha opened this" and the
 * system wins on duration, which is the split the spec asks for (#11) and which
 * falls out of the shapes: only the log carries a session, and only the system
 * carries a span.
 */
fun mergeLaunches(
    appId: String,
    logged: List<LaunchRecord>,
    entries: List<ForegroundEntry>,
): List<LaunchRecord> {
    val mine = logged.filter { it.appId == appId }
    val unmediated = resolveForegroundOpens(entries)
        .filter { it.appId == appId }
        .filterNot { entry ->
            mine.any { record ->
                !entry.at.isBefore(record.at) &&
                    !entry.at.isAfter(record.at.plus(SAME_LAUNCH_WINDOW))
            }
        }
        .map { LaunchRecord(appId = it.appId, at = it.at, session = null) }
    return (logged + unmediated).sortedBy { it.at }
}

/**
 * Whether Awareness can see what Android sees (#175, #18) — one value for the
 * whole surface, so every figure on it degrades for the same stated reason.
 */
sealed interface AwarenessUsage {

    /** Usage access is held right now; durations and unmediated opens are real. */
    data object Live : AwarenessUsage

    /**
     * Never granted. [offersTurnOn] is false once the education has been
     * delivered and declined: a past refusal degrades quietly rather than being
     * asked again, the rule every other ungranted slot already takes (ADR 0017).
     */
    data class Ungranted(val offersTurnOn: Boolean) : AwarenessUsage

    /** Granted at some point this session and gone now: the view says what stopped. */
    data object Revoked : AwarenessUsage
}

/**
 * Resolves the surface's usage state (#175), copying [resolveDigestSlot]'s arms.
 *
 * The one deliberate divergence is worth stating rather than reading as an
 * oversight. The digest's revocation arm is `!granted && (grantSeen ||
 * counts.isNotEmpty())` — it has a second discriminator because it has stored
 * counts under the day key to fall back on. Awareness stores no foreground
 * reading at all (ADR 0009): every figure here is read on demand and thrown
 * away. So [grantSeen] is the only evidence a grant ever existed, and
 * [AwarenessUsage.Revoked] is scoped to one composition **by construction**
 * rather than by an omission that could be fixed. Leaving Awareness and coming
 * back after a revocation reads as never-granted, which is what the digest
 * already ships.
 */
fun resolveAwarenessUsage(
    granted: Boolean,
    educationShown: Boolean,
    grantSeen: Boolean,
): AwarenessUsage = when {
    granted -> AwarenessUsage.Live
    grantSeen -> AwarenessUsage.Revoked
    else -> AwarenessUsage.Ungranted(offersTurnOn = !educationShown)
}

/** Why no span could be read (#175). Each one is a different sentence to the reader. */
enum class UnavailableReason {

    /** Usage access is not held, so nothing was measuring.  */
    NoUsageAccess,

    /**
     * The app is a work-profile clone, whose id carries a serial and resolves to
     * no package to look up. Android reports the main profile's statistics to
     * Bodha and no other's.
     */
    OtherProfile,

    /** Access is held and the read still came back with nothing at all. */
    NoReading,
}

/**
 * One app's time in front over a rendered window (#175) — or the reason there
 * is no such figure, which is a first-class answer here rather than a fallback.
 *
 * [None] and [Unavailable] are the two absences and they are not the same claim.
 * [None] is a successful measurement that found no foreground time; [Unavailable]
 * is no measurement. Collapsing them — or worse, either of them — into 0 would
 * tell the reader the app was never in front, which is a statement about their
 * behaviour made out of a gap in Bodha's own reach (#11).
 */
sealed interface AwarenessDuration {
    data class Span(val millis: Long) : AwarenessDuration
    data object None : AwarenessDuration
    data class Unavailable(val reason: UnavailableReason) : AwarenessDuration
}

/**
 * Resolves one app's foreground figure (#175). Total, in this arm order, and
 * every arm is an absence told apart from the others rather than merged into
 * one vague one.
 *
 * [reading] is the **whole map** rather than a `Long?` the caller already looked
 * up, and that is the load-bearing part of the signature: flattening first
 * destroys the difference between an app absent from a successful reading (a
 * real zero — it was measured and it was not in front) and no package to look up
 * at all (no measurement was possible). Only the second is [UnavailableReason.OtherProfile].
 *
 * [packageName] is null exactly where the app id carries a profile serial, which
 * is why that arm names the profile rather than the read.
 */
fun resolveAppDuration(
    usage: AwarenessUsage,
    packageName: String?,
    reading: Map<String, Long>?,
): AwarenessDuration = when {
    usage !is AwarenessUsage.Live ->
        AwarenessDuration.Unavailable(UnavailableReason.NoUsageAccess)
    reading == null -> AwarenessDuration.Unavailable(UnavailableReason.NoReading)
    packageName == null -> AwarenessDuration.Unavailable(UnavailableReason.OtherProfile)
    else -> reading[packageName]?.takeIf { it > 0 }
        ?.let { AwarenessDuration.Span(it) }
        ?: AwarenessDuration.None
}

/**
 * The one sentence stating an app's foreground time (#175, ADR 0013), whether
 * there is one to state or not.
 *
 * Total over both arguments on purpose: a row cannot contradict the surface it
 * sits on if the same function decides what both say. The figure alone cannot
 * word the degraded case — "no usage access" is the same absence whether the
 * user never granted it or turned it off ten minutes ago, and only the second
 * is worth telling them about — so the surface's state is the second argument.
 *
 * Every arm is a plain statement of what is or is not there. No delta, no
 * direction word, no ranking, and no arm that resolves to a bare 0 (ADR 0013):
 * a measured zero says "no foreground time recorded" in words, because a `0`
 * printed in a field is indistinguishable from a field nothing filled in.
 */
fun awarenessForegroundLine(duration: AwarenessDuration, usage: AwarenessUsage): String =
    when (duration) {
        is AwarenessDuration.Unavailable -> when (duration.reason) {
            UnavailableReason.NoUsageAccess ->
                if (usage is AwarenessUsage.Revoked) {
                    "Foreground time stopped when usage access was turned off"
                } else {
                    "Foreground time needs usage access"
                }
            UnavailableReason.OtherProfile -> "Foreground time is recorded for the main profile only"
            UnavailableReason.NoReading -> "Foreground time could not be read"
        }
        AwarenessDuration.None -> "No foreground time recorded"
        is AwarenessDuration.Span ->
            if (duration.millis < 60_000) "Under a minute in the foreground"
            else "${spanPhrase(duration.millis)} in the foreground"
    }

/**
 * What the App view says beneath its rows about where the rows came from (#175),
 * or null where there is nothing worth saying.
 *
 * Null while access is held, deliberately. Naming Android's own few-day
 * retention there would be honest and would also put a caveat under a view that
 * is working — and the launch log, which covers the whole window, is the spine
 * either way (ADR 0013).
 */
fun appOpensSourceLine(usage: AwarenessUsage): String? = when (usage) {
    AwarenessUsage.Live -> null
    is AwarenessUsage.Ungranted -> "Opens from notifications and other apps need usage access"
    AwarenessUsage.Revoked -> "Opens from notifications and other apps stopped when usage access was turned off"
}

/**
 * The route into the capability education (#157, #175). A label, not a sentence:
 * it names an outcome the user can choose, which is the one thing the degraded
 * state offers beyond stating itself.
 */
const val AWARENESS_TURN_ON_USAGE = "Turn on usage access"


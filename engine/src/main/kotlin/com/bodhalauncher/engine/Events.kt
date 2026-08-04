package com.bodhalauncher.engine

import java.time.Duration
import java.time.LocalDateTime

/**
 * The complete on-device event vocabulary (#25, ADR 0009). Fixed enum by design:
 * an event is a type, a timestamp and at most one duration — there is no field
 * that could carry an app name, content or free text.
 */
enum class EventType {
    OnboardingStepCompleted,
    PermissionEnabled,
    PermissionSkipped,
    HomeRendered,
    SearchUsed,
    SessionStarted,
    SessionEnded,
    AppLaunched,
    RepeatedOpenDetected,
    IntentPromptShown,
    IntentPromptAnswered,
    IntentPromptDismissed,
    OpenCheckDisplayed,
    OpenCheckTurnedBack,
    OpenCheckProceeded,
    FocusStarted,
    FocusPaused,
    FocusCompleted,
    FocusAbandoned,
    // Reserved for context suggestions (#14) so the enum doesn't churn (#25).
    SuggestionShown,
    SuggestionFeedback,
    PaywallShown,
    PurchaseCompleted,
    PurchaseAbandoned,
    PerformanceMark,
}

data class LoggedEvent(
    val type: EventType,
    val at: LocalDateTime,
    val valueMillis: Long? = null,
)

/**
 * The user-visible product metrics (#25), computed on-device; the user is the
 * analyst. A metric with nothing to measure is null — never a misleading zero.
 * There is deliberately no daily-active or time-in-app metric.
 */
data class ProductMetrics(
    val intentionalSessionRatio: Double?,
    val repeatedOpensPerDay: Double?,
    val medianUsefulActionMillis: Long?,
    val appSwitchingBurstsPerDay: Double?,
    val focusCompletionRate: Double?,
    val openCheckReturnRate: Double?,
)

/** Launches this close together, four or more in a row, count as one switching burst. */
private val BURST_SPAN: Duration = Duration.ofSeconds(60)
private const val BURST_SIZE = 4

/** Computes the metric set over the window [from, to). */
fun computeMetrics(events: List<LoggedEvent>, from: LocalDateTime, to: LocalDateTime): ProductMetrics {
    val window = events.filter { !it.at.isBefore(from) && it.at.isBefore(to) }.sortedBy { it.at }
    val days = Duration.between(from, to).toMillis() / 86_400_000.0
    fun count(type: EventType) = window.count { it.type == type }

    val sessions = count(EventType.SessionStarted)
    val launches = window.filter { it.type == EventType.AppLaunched }

    return ProductMetrics(
        intentionalSessionRatio = ratio(count(EventType.IntentPromptAnswered), sessions),
        repeatedOpensPerDay = count(EventType.RepeatedOpenDetected)
            .takeIf { it > 0 }?.let { it / days },
        medianUsefulActionMillis = medianUsefulAction(window),
        appSwitchingBurstsPerDay = countBursts(launches).takeIf { it > 0 }?.let { it / days },
        focusCompletionRate = ratio(count(EventType.FocusCompleted), count(EventType.FocusStarted)),
        openCheckReturnRate = ratio(count(EventType.OpenCheckTurnedBack), count(EventType.OpenCheckDisplayed)),
    )
}

private fun ratio(part: Int, whole: Int): Double? = if (whole == 0) null else part.toDouble() / whole

/** Median gap from each session start to its first app launch. */
private fun medianUsefulAction(window: List<LoggedEvent>): Long? {
    val samples = mutableListOf<Long>()
    var sessionStart: LocalDateTime? = null
    for (event in window) {
        when (event.type) {
            EventType.SessionStarted -> sessionStart = event.at
            EventType.AppLaunched -> sessionStart?.let {
                samples += Duration.between(it, event.at).toMillis()
                sessionStart = null
            }
            else -> Unit
        }
    }
    if (samples.isEmpty()) return null
    samples.sort()
    val mid = samples.size / 2
    return if (samples.size % 2 == 1) samples[mid] else (samples[mid - 1] + samples[mid]) / 2
}

/** Non-overlapping runs of [BURST_SIZE] launches inside [BURST_SPAN]. */
private fun countBursts(launches: List<LoggedEvent>): Int {
    var bursts = 0
    var i = 0
    while (i + BURST_SIZE <= launches.size) {
        val span = Duration.between(launches[i].at, launches[i + BURST_SIZE - 1].at)
        if (span <= BURST_SPAN) {
            bursts++
            i += BURST_SIZE
        } else {
            i++
        }
    }
    return bursts
}

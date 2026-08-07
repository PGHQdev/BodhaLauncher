package com.bodhalauncher.app.intent

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bodhalauncher.engine.IntentCategory
import com.bodhalauncher.engine.PromptDecision
import com.bodhalauncher.engine.SessionId
import com.bodhalauncher.engine.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * The read side of the intent records (#172): two of ADR 0013's three signals
 * come out of this file, and which session each belongs to is decided here or
 * left to the span.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class IntentRecordStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearFile() {
        File(context.filesDir, "intent_records.jsonl").delete()
    }

    private fun decision(session: Long) = PromptDecision(
        session = SessionId(session),
        at = Instant.now(),
        trigger = TriggerSource.Reflexive,
    )

    @Test
    fun `a prompt answer carries the session it was asked under`() {
        val store = IntentRecordStore(context)
        store.appendSelection(decision(7), IntentCategory.FindSomething, text = null)

        val signals = store.signalsSince(Instant.EPOCH)

        assertEquals(1, signals.size)
        assertEquals(7L, signals.single().session)
    }

    @Test
    fun `an Open Check intention carries no session, leaving it to the span`() {
        val store = IntentRecordStore(context)
        store.appendOpenCheckIntention("reply to Sam")

        assertEquals(listOf<Long?>(null), store.signalsSince(Instant.EPOCH).map { it.session })
    }

    @Test
    fun `a dismissal states nothing and is not a signal`() {
        val store = IntentRecordStore(context)
        store.appendDismissal(decision(3))

        assertEquals(emptyList<Long?>(), store.signalsSince(Instant.EPOCH).map { it.session })
    }

    @Test
    fun `records before the cut are not read`() {
        val store = IntentRecordStore(context)
        store.appendOpenCheckIntention("earlier")

        assertEquals(emptyList<Any?>(), store.signalsSince(Instant.now().plusSeconds(60)))
    }

    @Test
    fun `an unreadable line is skipped rather than failing the read`() {
        val store = IntentRecordStore(context)
        store.appendOpenCheckIntention("kept")
        File(context.filesDir, "intent_records.jsonl").appendText("not json\n")
        store.appendSelection(decision(9), IntentCategory.FindSomething, text = null)

        assertEquals(listOf<Long?>(null, 9L), store.signalsSince(Instant.EPOCH).map { it.session })
    }

    @Test
    fun `no file at all reads as no signals`() {
        assertEquals(emptyList<Any?>(), IntentRecordStore(context).signalsSince(Instant.EPOCH))
    }
}

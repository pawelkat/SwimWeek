package com.swimweek.app.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StzhDisturberParserTest {

    private val blaesiSnippet = """
        <stzh-pagetitle>
          <stzh-show slot="disturber"
              hide-from-date="2026-08-16T23:30:00.000+02:00"
              show-from-date="2026-07-27T00:05:00.000+02:00">
            <stzh-disturber>
              <stzh-clamp slot="label" lines="3">Geschlossen bis 16. August (Revision)</stzh-clamp>
            </stzh-disturber>
          </stzh-show>
        </stzh-pagetitle>
    """.trimIndent()

    private val promoSnippet = """
        <stzh-show slot="disturber"
            show-from-date="2026-05-01T03:00:00.000+02:00"
            hide-from-date="2026-05-16T15:29:00.000+02:00">
          <stzh-disturber>
            <stzh-clamp slot="label">Jetzt Eintritte und Abos online kaufen</stzh-clamp>
          </stzh-disturber>
        </stzh-show>
    """.trimIndent()

    @Test
    fun parsesClosureWithIsoDates() {
        val notices = StzhDisturberParser.parse(blaesiSnippet)
        assertEquals(1, notices.size)
        val n = notices.first()
        assertEquals("Geschlossen bis 16. August (Revision)", n.label)
        assertTrue(n.isClosureLike)
        assertEquals(Instant.parse("2026-07-26T22:05:00Z"), n.showFrom) // +02:00
        assertEquals(Instant.parse("2026-08-16T21:30:00Z"), n.hideFrom)
        assertTrue(n.isActiveAt(Instant.parse("2026-08-10T12:00:00Z")))
        assertFalse(n.isActiveAt(Instant.parse("2026-08-17T12:00:00Z")))
    }

    @Test
    fun promoIsNotClosureLike() {
        val notices = StzhDisturberParser.parse(promoSnippet)
        assertEquals(1, notices.size)
        assertFalse(notices.first().isClosureLike)
    }

    @Test
    fun emptyHtml() {
        assertTrue(StzhDisturberParser.parse("").isEmpty())
        assertTrue(StzhDisturberParser.parse("<html></html>").isEmpty())
    }
}

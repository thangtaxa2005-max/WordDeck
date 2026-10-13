package vn.worddeck

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test
    fun forgettingResetsRepetitionAndSchedulesToday() {
        val p = Scheduler.rate(Progress("c", interval = 20, repetitions = 4, due = 150), 0, 100)
        assertEquals(0, p.repetitions)
        assertEquals(100L, p.due)
        assertEquals(1, p.mistakes)
    }

    @Test
    fun successfulReviewsExpandIntervals() {
        var p = Progress("c")
        p = Scheduler.rate(p, 2, 100)
        assertEquals(1, p.interval)
        p = Scheduler.rate(p, 2, 101)
        assertEquals(6, p.interval)
        p = Scheduler.rate(p, 2, 107)
        assertEquals(15, p.interval)
        assertEquals(122L, p.due)
    }

    @Test
    fun easeNeverFallsBelowMinimum() {
        var p = Progress("c")
        repeat(100) { p = Scheduler.rate(p, 0, 100) }
        assertEquals(1.3, p.ease, 0.0001)
    }

    @Test
    fun csvPreservesQuotedCommaNewlineAndEscapedQuote() {
        val c =
            Importer.parse(
                    "word,meaning,phonetic,kind,example\r\nhello,\"xin chào, bạn\",,noun,\"Say \"\"hello\"\"\nagain\"",
                    "s",
                    true,
                )
                .single()
        assertEquals("xin chào, bạn", c.meaning)
        assertEquals("Say \"hello\"\nagain", c.example)
    }

    @Test
    fun textSupportsBareWordsAndHyphenatedWords() {
        val c = Importer.parse("well-known - nổi tiếng\nhello\n\nbook\tsách", "s")
        assertEquals(3, c.size)
        assertEquals("well-known", c[0].word)
        assertEquals("", c[1].meaning)
        assertEquals("sách", c[2].meaning)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedCsvFails() {
        Importer.parse("word,\"unfinished", "s", true)
    }

    @Test
    fun matchingIgnoresCaseAndExtraWhitespace() {
        assertEquals(normalized("  Good   MORNING "), normalized("good morning"))
    }
}

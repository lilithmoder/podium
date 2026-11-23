package app.podiumpodcasts.podium.background.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonChapterParserTest {

    @Test
    fun parsesSpecExample() {
        val result = parseJsonChapters(
            """
            {
              "version": "1.2.0",
              "chapters": [
                { "startTime": 0, "title": "Intro", "img": "https://example.com/intro.jpg" },
                { "startTime": 168.5, "title": "Hearing Aids", "url": "https://example.com/aids", "endTime": 290 },
                { "startTime": 290, "title": "Progress", "location": { "name": "Somewhere", "geo": "geo:1,2" } }
              ]
            }
            """.trimIndent(),
            durationMs = 600_000
        )

        assertEquals(listOf(0L, 168_500L, 290_000L), result.map { it.startMs })
        assertEquals(listOf("Intro", "Hearing Aids", "Progress"), result.map { it.title })
        assertEquals("https://example.com/intro.jpg", result[0].imageUrl)
        assertEquals("https://example.com/aids", result[1].url)
        assertEquals(290_000L, result[1].endMs)
        assertNull(result[0].endMs)
    }

    @Test
    fun dropsHiddenAndInvalidChapters() {
        val result = parseJsonChapters(
            """
            {"chapters": [
              {"startTime": 30, "title": "B"},
              {"startTime": 10, "title": "Hidden", "toc": false},
              {"title": "No start"},
              {"startTime": 0, "title": "A"},
              {"startTime": 0, "title": "Duplicate"},
              {"startTime": 9999, "title": "Beyond end"}
            ]}
            """.trimIndent(),
            durationMs = 60_000
        )

        assertEquals(listOf("A", "B"), result.map { it.title })
    }

    @Test
    fun emptyOrSingleChapterReturnsEmpty() {
        assertTrue(parseJsonChapters("""{"version":"1.2.0"}""", null).isEmpty())
        assertTrue(parseJsonChapters("""{"chapters":[{"startTime":0,"title":"Only"}]}""", null).isEmpty())
    }

    @Test(expected = Exception::class)
    fun malformedJsonThrows() {
        parseJsonChapters("<html>not json</html>", null)
    }
}

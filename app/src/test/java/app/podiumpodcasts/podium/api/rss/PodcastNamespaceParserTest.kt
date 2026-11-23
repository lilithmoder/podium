package app.podiumpodcasts.podium.api.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastNamespaceParserTest {

    private fun feed(items: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:podcast="https://podcastindex.org/namespace/1.0"
             xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>Show</title>
            <podcast:chapters url="https://example.com/channel-level-is-ignored.json" type="application/json+chapters"/>
            $items
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun mapsGuidToChaptersUrl() {
        val result = parseChapterUrls(
            feed(
                """
                <item>
                  <title>One</title>
                  <guid isPermaLink="false">  ep-1  </guid>
                  <podcast:chapters url="https://example.com/1.json" type="application/json+chapters"/>
                </item>
                <item>
                  <title>Two, no chapters</title>
                  <guid>ep-2</guid>
                </item>
                <item>
                  <podcast:chapters url="https://example.com/3.json" type="application/json+chapters"/>
                  <guid><![CDATA[ep-3]]></guid>
                </item>
                """.trimIndent()
            )
        )

        assertEquals(
            mapOf(
                "ep-1" to "https://example.com/1.json",
                "ep-3" to "https://example.com/3.json"
            ),
            result
        )
    }

    @Test
    fun decodesEntitiesInGuid() {
        val result = parseChapterUrls(
            feed(
                """
                <item>
                  <guid>https://example.com/?p=1&amp;v=2</guid>
                  <podcast:chapters url="https://example.com/1.json" type="application/json+chapters"/>
                </item>
                """.trimIndent()
            )
        )

        assertEquals(mapOf("https://example.com/?p=1&v=2" to "https://example.com/1.json"), result)
    }

    @Test
    fun unknownEntitiesDoNotBreakParsing() {
        val result = parseChapterUrls(
            feed(
                """
                <item>
                  <description>Hello&nbsp;world</description>
                  <guid>ep-1</guid>
                  <podcast:chapters url="https://example.com/1.json" type="application/json+chapters"/>
                </item>
                """.trimIndent()
            )
        )

        assertEquals(mapOf("ep-1" to "https://example.com/1.json"), result)
    }

    @Test
    fun malformedFeedKeepsEarlierItems() {
        val result = parseChapterUrls(
            feed(
                """
                <item>
                  <guid>ep-1</guid>
                  <podcast:chapters url="https://example.com/1.json" type="application/json+chapters"/>
                </item>
                <item>
                  <guid>ep-2</guid>
                  <broken
                """.trimIndent()
            )
        )

        assertEquals(mapOf("ep-1" to "https://example.com/1.json"), result)
    }

    @Test
    fun toleratesLeadingWhitespaceAndBom() {
        val xml = "\uFEFF\n  " + feed(
            """
            <item>
              <guid>ep-1</guid>
              <podcast:chapters url="https://example.com/1.json" type="application/json+chapters"/>
            </item>
            """.trimIndent()
        ).trimStart()

        assertEquals(mapOf("ep-1" to "https://example.com/1.json"), parseChapterUrls(xml))
    }

    @Test
    fun feedWithoutNamespaceReturnsEmpty() {
        assertTrue(parseChapterUrls("<rss><channel><item><guid>x</guid></item></channel></rss>").isEmpty())
    }
}

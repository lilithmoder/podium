package app.podiumpodcasts.podium.background.chapters

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.UrlLinkFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class Id3ChapterParserTest {

    private fun chapter(
        id: String,
        startMs: Int,
        endMs: Int = -1,
        title: String? = null,
        url: String? = null
    ): ChapterFrame {
        val subFrames = buildList<Id3Frame> {
            title?.let { add(TextInformationFrame("TIT2", null, listOf(it))) }
            url?.let { add(UrlLinkFrame("WXXX", null, it)) }
        }
        return ChapterFrame(id, startMs, endMs, -1L, -1L, subFrames.toTypedArray())
    }

    @Test
    fun sortsByStartTimeAndReadsTitleAndUrl() {
        val result = parseId3Chapters(
            listOf(
                Metadata(
                    chapter("c2", 60_000, 120_000, title = "Second"),
                    chapter("c1", 0, 60_000, title = "First", url = "https://example.com")
                )
            ),
            durationMs = 120_000
        )

        assertEquals(listOf(0L, 60_000L), result.map { it.startMs })
        assertEquals(listOf("First", "Second"), result.map { it.title })
        assertEquals("https://example.com", result[0].url)
        assertEquals(60_000L, result[0].endMs)
    }

    @Test
    fun missingTitleAndUnsetEndAreNull() {
        val result = parseId3Chapters(
            listOf(Metadata(chapter("a", 0), chapter("b", 10_000, title = "  "))),
            durationMs = null
        )

        assertEquals(2, result.size)
        assertNull(result[0].title)
        assertNull(result[1].title)
        assertNull(result[0].endMs)
    }

    @Test
    fun dropsDuplicateStartsAndOutOfRangeChapters() {
        val result = parseId3Chapters(
            listOf(
                Metadata(
                    chapter("a", 0, title = "A"),
                    chapter("dup", 0, title = "A again"),
                    chapter("b", 30_000, title = "B"),
                    chapter("late", 90_000, title = "Beyond end")
                )
            ),
            durationMs = 60_000
        )

        assertEquals(listOf("A", "B"), result.map { it.title })
    }

    @Test
    fun singleChapterReturnsEmpty() {
        val result = parseId3Chapters(
            listOf(Metadata(chapter("only", 0, title = "Only"))),
            durationMs = 60_000
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresNonChapterMetadataAndCombinesEntries() {
        val result = parseId3Chapters(
            listOf(
                Metadata(TextInformationFrame("TIT2", null, listOf("Episode title"))),
                Metadata(chapter("a", 0, title = "A")),
                Metadata(chapter("b", 5_000, title = "B"))
            ),
            durationMs = 0
        )

        assertEquals(listOf("A", "B"), result.map { it.title })
    }
}

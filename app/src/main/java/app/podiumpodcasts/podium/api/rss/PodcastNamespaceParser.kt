package app.podiumpodcasts.podium.api.rss

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming

private const val PODCAST_NAMESPACE = "https://podcastindex.org/namespace/1.0"

/**
 * Reads `<podcast:chapters url="...">` from each `<item>` of an RSS feed, which the RSS
 * parser library does not expose.
 *
 * Returns item guid (trimmed) -> chapters URL. Items without a guid or without chapters are
 * left out. Best effort: if the XML turns out to be malformed, the items read up to that
 * point are still returned.
 */
fun parseChapterUrls(xml: String): Map<String, String> {
    val result = mutableMapOf<String, String>()

    var inItem = false
    var inGuid = false
    val guid = StringBuilder()
    var chaptersUrl: String? = null

    try {
        // an XML declaration must come first; tolerate leading whitespace / BOM
        val reader = xmlStreaming.newGenericReader(xml.trimStart('﻿', ' ', '\t', '\r', '\n'))

        while(reader.hasNext()) {
            when(reader.next()) {
                EventType.START_ELEMENT -> when {
                    reader.localName == "item" -> {
                        inItem = true
                        guid.clear()
                        chaptersUrl = null
                    }

                    inItem && reader.localName == "guid" && reader.prefix.isEmpty() -> {
                        inGuid = true
                        guid.clear()
                    }

                    inItem && reader.isPodcastChapters() -> {
                        chaptersUrl = reader.getAttributeValue(null, "url")
                            ?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }

                EventType.TEXT, EventType.CDSECT -> if(inGuid) guid.append(reader.text)

                EventType.ENTITY_REF -> if(inGuid && reader.isKnownEntity) guid.append(reader.text)

                EventType.END_ELEMENT -> when(reader.localName) {
                    "guid" -> inGuid = false

                    "item" -> {
                        val key = guid.toString().trim()
                        val url = chaptersUrl
                        if(inItem && key.isNotEmpty() && url != null) result[key] = url

                        inItem = false
                        inGuid = false
                    }
                }

                else -> {}
            }
        }
    } catch(e: Exception) {
        // malformed feed: keep what was read so far
    }

    return result
}

/** Matches both namespace-resolved and prefix-only readings of `podcast:chapters`. */
private fun XmlReader.isPodcastChapters(): Boolean {
    if(localName == "podcast:chapters") return true
    if(localName != "chapters") return false
    return namespaceURI == PODCAST_NAMESPACE || prefix == "podcast"
}

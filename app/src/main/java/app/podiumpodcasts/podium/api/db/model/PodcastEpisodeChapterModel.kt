package app.podiumpodcasts.podium.api.db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index

/**
 * Where a chapter list came from. When several sources exist for an episode,
 * [JSON] (podcast:chapters) is preferred over [ID3] (embedded in the audio file).
 */
object ChapterSource {
    const val ID3 = "ID3"
    const val JSON = "JSON"
}

@Entity(
    tableName = "podcastEpisodeChapter",
    primaryKeys = ["episodeId", "source", "position"],
    foreignKeys = [ForeignKey(
        entity = PodcastEpisodeModel::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("episodeId"),
        onDelete = CASCADE
    )],
    indices = [Index("episodeId")]
)
data class PodcastEpisodeChapterModel(
    @ColumnInfo("episodeId")
    val episodeId: String,
    @ColumnInfo("source")
    val source: String,
    @ColumnInfo("position")
    val position: Int,
    @ColumnInfo("startMs")
    val startMs: Long,
    @ColumnInfo("endMs")
    val endMs: Long?,
    @ColumnInfo("title")
    val title: String?,
    @ColumnInfo("url")
    val url: String?,
    @ColumnInfo("imageUrl")
    val imageUrl: String?
)

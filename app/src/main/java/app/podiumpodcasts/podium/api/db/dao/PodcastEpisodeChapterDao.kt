package app.podiumpodcasts.podium.api.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.podiumpodcasts.podium.api.db.model.ChapterSource
import app.podiumpodcasts.podium.api.db.model.PodcastEpisodeChapterModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface PodcastEpisodeChapterDao {

    /**
     * Chapters of the preferred source (see [ChapterSource]) for [episodeId] (JSON over ID3), ordered by position.
     */
    fun observe(episodeId: String): Flow<List<PodcastEpisodeChapterModel>> =
        _observeAll(episodeId).map { rows ->
            val preferred = rows.firstOrNull()?.source
            rows.filter { it.source == preferred }
        }

    @Transaction
    suspend fun replaceForSource(
        episodeId: String,
        source: String,
        chapters: List<PodcastEpisodeChapterModel>
    ) {
        _deleteForSource(episodeId, source)
        _insertAll(chapters)
    }

    @Query(
        """
        SELECT * FROM podcastEpisodeChapter WHERE episodeId = :episodeId
        ORDER BY CASE source WHEN 'JSON' THEN 0 ELSE 1 END, position
        """
    )
    fun _observeAll(episodeId: String): Flow<List<PodcastEpisodeChapterModel>>

    @Query("DELETE FROM podcastEpisodeChapter WHERE episodeId = :episodeId AND source = :source")
    suspend fun _deleteForSource(episodeId: String, source: String)

    @Insert
    suspend fun _insertAll(chapters: List<PodcastEpisodeChapterModel>)
}

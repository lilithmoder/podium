package app.podiumpodcasts.podium.ui.dialog.bottomsheet.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.podiumpodcasts.podium.R
import app.podiumpodcasts.podium.api.db.model.PodcastEpisodeChapterModel
import app.podiumpodcasts.podium.ui.component.common.SwitchableDynamicMaterialExpressiveTheme
import app.podiumpodcasts.podium.ui.component.settings.SettingsListItem
import app.podiumpodcasts.podium.ui.formatPlayerTime
import app.podiumpodcasts.podium.ui.helper.LocalDatabase
import app.podiumpodcasts.podium.ui.helper.LocalSettingsRepository
import app.podiumpodcasts.podium.ui.theme.Typography
import app.podiumpodcasts.podium.ui.vm.MediaPlayerViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * Chapters of [episodeId] from the database (filled by PlaybackService), empty if none.
 */
@Composable
fun rememberChapters(episodeId: String?): State<List<PodcastEpisodeChapterModel>> {
    val db = LocalDatabase.current
    val flow = remember(episodeId) {
        episodeId?.let { db.podcastEpisodeChapters().observe(it) }
            ?: flowOf(emptyList())
    }
    return flow.collectAsState(emptyList())
}

/** Index of the chapter playing at [positionMs], or -1 before the first chapter. */
fun List<PodcastEpisodeChapterModel>.currentChapterIndex(positionMs: Long): Int =
    indexOfLast { it.startMs <= positionMs }

/**
 * Seek target for "previous chapter": the current chapter's start if more than
 * [restartThresholdMs] into it, otherwise the previous chapter's start.
 */
fun List<PodcastEpisodeChapterModel>.previousChapterStart(
    positionMs: Long,
    restartThresholdMs: Long = 3000L
): Long {
    val index = currentChapterIndex(positionMs)
    if(index < 0) return 0L

    val current = this[index]
    if(index == 0 || positionMs - current.startMs > restartThresholdMs) return current.startMs
    return this[index - 1].startMs
}

/** Seek target for "next chapter", or null on the last chapter. */
fun List<PodcastEpisodeChapterModel>.nextChapterStart(positionMs: Long): Long? =
    getOrNull(currentChapterIndex(positionMs) + 1)?.startMs

@Composable
fun chapterTitle(chapter: PodcastEpisodeChapterModel): String =
    chapter.title ?: stringResource(R.string.common_chapter_fallback, chapter.position + 1)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaPlayerChaptersBottomSheet(
    onDismiss: () -> Unit
) {
    val settingsRepository = LocalSettingsRepository.current
    val vm = viewModel<MediaPlayerViewModel>()

    val enableArtworkColors = settingsRepository.appearance.enableArtworkColors.collectAsState(true)

    val chapters by rememberChapters(vm.metadataEpisodeId)
    val progressState = vm.getProgressState()

    // read the position at click time; only recompose when the current chapter changes
    fun positionMs() = (progressState.value * vm.currentDuration).toLong()
    val currentIndex by remember(chapters) {
        derivedStateOf { chapters.currentChapterIndex(positionMs()) }
    }

    // header is item 0, chapters start at 1
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex + 1).coerceAtLeast(0)
    )

    SwitchableDynamicMaterialExpressiveTheme(
        enable = enableArtworkColors.value,
        seedColor = Color(vm.metadataImageSeedColor ?: 1)
    ) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(),
            onDismissRequest = onDismiss
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            enabled = chapters.isNotEmpty(),
                            onClick = { vm.seek(chapters.previousChapterStart(positionMs())) }
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                stringResource(R.string.common_action_previous_chapter)
                            )
                        }

                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.common_chapters),
                            style = Typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            enabled = currentIndex < chapters.lastIndex,
                            onClick = { chapters.nextChapterStart(positionMs())?.let { vm.seek(it) } }
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                stringResource(R.string.common_action_next_chapter)
                            )
                        }
                    }
                }

                items(
                    count = chapters.size,
                    key = { index -> chapters[index].position }
                ) { index ->
                    val chapter = chapters[index]

                    SettingsListItem(
                        label = chapterTitle(chapter),
                        description = formatPlayerTime(chapter.startMs, vm.currentDuration),
                        selected = index == currentIndex,
                        index = index,
                        count = chapters.size,
                        onClick = { vm.seek(chapter.startMs) }
                    )
                }
            }
        }
    }
}

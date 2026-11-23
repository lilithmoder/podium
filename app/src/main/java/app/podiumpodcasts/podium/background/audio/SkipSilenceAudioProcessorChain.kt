package app.podiumpodcasts.podium.background.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import app.podiumpodcasts.podium.R

/**
 * How aggressively silence between words and sentences is shortened.
 *
 * Parameters map to [SilenceSkippingAudioProcessor]'s constructor. They are starting points
 * and may need tuning by ear.
 */
enum class SkipSilenceLevel(
    @param:StringRes val labelId: Int,
    val minimumSilenceDurationUs: Long,
    val silenceRetentionRatio: Float,
    val maxSilenceToKeepDurationUs: Long,
    val minVolumeToKeepPercentageWhenMuting: Int
) {
    OFF(R.string.common_skip_silence_off, 0L, 1f, 0L, 100),
    MILD(R.string.common_skip_silence_mild, 250_000L, 0.5f, 1_000_000L, 10),
    MEDIUM(R.string.common_skip_silence_medium, 100_000L, 0.2f, 500_000L, 10),
    STRONG(R.string.common_skip_silence_strong, 50_000L, 0.05f, 150_000L, 5);

    companion object {
        const val SILENCE_THRESHOLD_LEVEL: Short = 1024

        fun fromName(name: String?): SkipSilenceLevel =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * Audio processor chain with one [SilenceSkippingAudioProcessor] per [SkipSilenceLevel].
 *
 * [SilenceSkippingAudioProcessor] is final and its thresholds are fixed at construction, so
 * levels are switched by enabling exactly one processor. The selected processor is applied
 * by [DefaultAudioSink] whenever skip silence is (re-)enabled on the player.
 *
 * Mirrors [DefaultAudioSink.DefaultAudioProcessorChain]: silence skipping before Sonic.
 */
@OptIn(UnstableApi::class)
class SkipSilenceAudioProcessorChain : AudioProcessorChain {

    /** Written on the main thread, read on the playback thread. */
    @Volatile
    var level: SkipSilenceLevel = SkipSilenceLevel.OFF

    private val silenceProcessors: Map<SkipSilenceLevel, SilenceSkippingAudioProcessor> =
        SkipSilenceLevel.entries
            .filter { it != SkipSilenceLevel.OFF }
            .associateWith { level ->
                SilenceSkippingAudioProcessor(
                    level.minimumSilenceDurationUs,
                    level.silenceRetentionRatio,
                    level.maxSilenceToKeepDurationUs,
                    level.minVolumeToKeepPercentageWhenMuting,
                    SkipSilenceLevel.SILENCE_THRESHOLD_LEVEL
                )
            }

    private val sonicAudioProcessor = SonicAudioProcessor()

    private val audioProcessors: Array<AudioProcessor> =
        buildList<AudioProcessor> {
            addAll(silenceProcessors.values)
            add(sonicAudioProcessor)
        }.toTypedArray()

    override fun getAudioProcessors(): Array<AudioProcessor> = audioProcessors

    override fun applyPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): PlaybackParameters {
        sonicAudioProcessor.setSpeed(playbackParameters.speed)
        sonicAudioProcessor.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        val selected = level
        silenceProcessors.forEach { (processorLevel, processor) ->
            processor.setEnabled(skipSilenceEnabled && processorLevel == selected)
        }
        return skipSilenceEnabled && selected != SkipSilenceLevel.OFF
    }

    override fun getMediaDuration(playoutDuration: Long): Long {
        return if(sonicAudioProcessor.isActive) {
            sonicAudioProcessor.getMediaDuration(playoutDuration)
        } else {
            playoutDuration
        }
    }

    /**
     * Sum over all processors: only one is enabled at a time, the others report 0. The sink
     * uses this to map output position back to media position, so it must cover every
     * processor that can drop frames.
     */
    override fun getSkippedOutputFrameCount(): Long =
        silenceProcessors.values.sumOf { it.skippedFrames }
}

/**
 * Renderers factory whose audio sink uses [chain] instead of the default processor chain.
 */
@OptIn(UnstableApi::class)
class SkipSilenceRenderersFactory(
    context: Context,
    private val chain: SkipSilenceAudioProcessorChain
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessorChain(chain)
            .build()
    }
}

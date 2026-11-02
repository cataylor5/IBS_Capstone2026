package io.github.cataylor5.ibs

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun IbsLaunchScreen(
    lifecycle: Lifecycle,
    content: @Composable () -> Unit
) {
    var introFinished by rememberSaveable { mutableStateOf(false) }
    // Remembers that the intro finished when the activity is recreated.

    var introRemoved by remember { mutableStateOf(introFinished) }
    // Keeps the video visible until its fade finishes.

    val introOpacity = remember { Animatable(1f) }
    // Starts the video layer fully visible.

    LaunchedEffect(introFinished) {
        if (introFinished && !introRemoved) {
            introOpacity.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300)
            )
            // Fades the video away over 300 milliseconds.

            introRemoved = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (introFinished || introRemoved) {
            content()
        }
        // Waits until the intro finishes before building the homepage.

        if (!introRemoved) {
            SplashVideo(
                lifecycle = lifecycle,
                onFinished = { introFinished = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = introOpacity.value
                    }
            )

            BackHandler {
                introFinished = true
            }
            // Lets the Back button skip the intro.
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplashVideo(
    lifecycle: Lifecycle,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    // Creates a silent player that does not loop.

    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnFinished()
                }
            }
            // Starts the fade when the video finishes.

            override fun onPlayerError(error: PlaybackException) {
                currentOnFinished()
            }
            // Continues to the homepage if playback fails.
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()

                Lifecycle.Event.ON_STOP -> {
                    player.pause()
                    currentOnFinished()
                }
                // Stops playback if the user leaves the app.

                else -> Unit
            }
        }

        player.addListener(listener)
        lifecycle.addObserver(observer)

        val videoUri =
            "android.resource://${context.packageName}/${R.raw.ibs_splash}"
        // Finds the video bundled in the raw folder.

        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        player.playWhenReady =
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
        // Frees the video player after the intro is removed.
    }

    LaunchedEffect(player) {
        delay(8_000)
        currentOnFinished()
    }
    // Falls back to the homepage if playback gets stuck.

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            val view = LayoutInflater.from(viewContext)
                .inflate(R.layout.splash_video, null, false) as PlayerView

            view.player = player
            view
        },
        update = { view ->
            view.player = player
        }
    )
}
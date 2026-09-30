package com.tidyorwhiny.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tidyorwhiny.app.ui.screens.AnalyzingScreen
import com.tidyorwhiny.app.ui.screens.CameraScreen
import com.tidyorwhiny.app.ui.screens.HomeScreen
import com.tidyorwhiny.app.ui.screens.MessResultScreen
import com.tidyorwhiny.app.ui.screens.PhotoPreviewScreen
import com.tidyorwhiny.app.ui.screens.RecorderScreen
import com.tidyorwhiny.app.ui.screens.WhineResultScreen
import com.tidyorwhiny.app.ui.theme.Cream
import com.tidyorwhiny.app.ui.theme.TwTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (BuildConfig.DEBUG && savedInstanceState == null) {
            intent.getStringExtra("demo")?.let { Demo.screen(this, it) }?.let(vm::show)
        }
        setContent { TwTheme { App(vm) } }
    }
}

/** Screen depth, so moving deeper slides in from the right and going back from the left. */
private val Screen.depth
    get() = when (this) {
        Screen.Home -> 0
        Screen.Camera, Screen.Recorder -> 1
        is Screen.PhotoPreview -> 2
        is Screen.Analyzing -> 3
        is Screen.MessResult, is Screen.WhineResult -> 4
    }

@Composable
private fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }
    Box(Modifier.fillMaxSize().background(Cream)) {
        AnimatedContent(
            vm.screen,
            transitionSpec = {
                val dir = if (targetState.depth >= initialState.depth) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir / 4 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it * dir / 4 } + fadeOut(tween(200)))
            },
            contentKey = { it.javaClass },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(onMess = { vm.open(Check.Mess) }, onWhine = { vm.open(Check.Whine) })
                Screen.Camera -> CameraScreen(onBack = vm::home, onPhoto = vm::onPhoto)
                is Screen.PhotoPreview -> PhotoPreviewScreen(s.photo, onBack = vm::retake, onRetake = vm::retake, onSend = vm::sendPhoto)
                Screen.Recorder -> RecorderScreen(onBack = vm::home, onSend = vm::sendWords)
                is Screen.Analyzing -> AnalyzingScreen(s.check, s.error, onCancel = vm::back, onRetry = vm::retry)
                is Screen.MessResult -> MessResultScreen(s.photo, s.verdict, onAgain = { vm.open(Check.Mess) }, onHome = vm::home)
                is Screen.WhineResult -> WhineResultScreen(s.transcript, s.verdict, onAgain = { vm.open(Check.Whine) }, onHome = vm::home)
            }
        }
    }
}

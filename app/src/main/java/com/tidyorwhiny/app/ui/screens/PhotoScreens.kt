package com.tidyorwhiny.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tidyorwhiny.app.R
import com.tidyorwhiny.app.ui.components.Mascot
import com.tidyorwhiny.app.ui.components.Pill
import com.tidyorwhiny.app.ui.components.RoundIconButton
import com.tidyorwhiny.app.ui.components.StepDots
import com.tidyorwhiny.app.ui.components.StickerButton
import com.tidyorwhiny.app.ui.components.TwIcons
import com.tidyorwhiny.app.ui.components.TwTopBar
import com.tidyorwhiny.app.ui.components.sticker
import com.tidyorwhiny.app.ui.theme.Cream
import com.tidyorwhiny.app.ui.theme.Ink
import com.tidyorwhiny.app.ui.theme.Line
import com.tidyorwhiny.app.ui.theme.Night
import com.tidyorwhiny.app.ui.theme.NightSurface
import com.tidyorwhiny.app.ui.theme.Orange
import com.tidyorwhiny.app.ui.theme.Paper
import com.tidyorwhiny.app.ui.theme.TwType
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val PHOTO_SIDE = 1600   // long side kept; the ask sends 1280

@Composable
fun CameraScreen(onBack: () -> Unit, onPhoto: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { decode(context, it) }?.let(onPhoto)
    }
    val pickPhoto = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(Modifier.fillMaxSize().background(Night).statusBarsPadding().navigationBarsPadding()) {
        TwTopBar(stringResource(R.string.mess_check), onBack, backLabel = stringResource(R.string.back), dark = true) {
            StepDots(1, Orange, dark = true)
        }
        if (granted) LiveCamera(onPhoto, pickPhoto)
        else NoCamera(onAllow = { ask.launch(Manifest.permission.CAMERA) }, onGallery = pickPhoto)
    }
}

@Composable
private fun LiveCamera(onPhoto: (Bitmap) -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val blink = remember { Animatable(0f) }

    LaunchedEffect(lens) {
        try {
            val provider = cameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.Builder().requireLensFacing(lens).build(), preview, capture)
            failed = false
        } catch (e: Exception) {
            failed = true
        }
    }
    LaunchedEffect(flash) { capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(32.dp)).background(NightSurface)
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Viewfinder(Modifier.fillMaxSize())
            Text(
                stringResource(if (failed) R.string.camera_failed else R.string.camera_hint),
                style = TwType.small, color = Paper,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp, start = 24.dp, end = 24.dp)
                    .clip(Pill).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            RoundIconButton(
                if (flash) TwIcons.Flash else TwIcons.FlashOff, stringResource(R.string.flash), { flash = !flash },
                Modifier.align(Alignment.BottomEnd).padding(16.dp), dark = true,
            )
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value }.background(Paper))
        }
        Row(
            Modifier.fillMaxWidth().height(148.dp).padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(TwIcons.Gallery, stringResource(R.string.pick_gallery), onGallery, dark = true, size = 56.dp)
            Shutter(enabled = !busy && !failed, label = stringResource(R.string.take_photo)) {
                busy = true
                scope.launch { blink.snapTo(0.9f); blink.animateTo(0f, tween(260)) }
                capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.upright()
                        image.close()
                        busy = false
                        onPhoto(bmp)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        busy = false
                    }
                })
            }
            RoundIconButton(TwIcons.Retry, stringResource(R.string.switch_camera), {
                lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            }, dark = true, size = 56.dp)
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(88.dp).clip(CircleShape).border(5.dp, Paper, CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(66.dp).clip(CircleShape).background(if (enabled) Orange else Orange.copy(alpha = 0.4f)))
    }
}

/** Corner brackets and thirds over the live picture. */
@Composable
private fun Viewfinder(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val line = Color.White.copy(alpha = 0.16f)
        for (k in 1..2) {
            drawLine(line, Offset(w * k / 3, 0f), Offset(w * k / 3, h), 2f)
            drawLine(line, Offset(0f, h * k / 3), Offset(w, h * k / 3), 2f)
        }
        val m = 22.dp.toPx(); val a = 36.dp.toPx(); val s = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(Orange, Offset(x, y), Offset(x + a * dx, y), s.width, StrokeCap.Round)
            drawLine(Orange, Offset(x, y), Offset(x, y + a * dy), s.width, StrokeCap.Round)
        }
        corner(m, m + 44.dp.toPx(), 1f, 1f)
        corner(w - m, m + 44.dp.toPx(), -1f, 1f)
        corner(m, h - m, 1f, -1f)
        corner(w - m, h - m, -1f, -1f)
    }
}

@Composable
private fun NoCamera(onAllow: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mascot(Modifier.size(150.dp), bob = true)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.camera_permission_title), style = TwType.title, color = Paper,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.camera_permission_body), style = TwType.body, color = Paper.copy(alpha = 0.75f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        StickerButton(stringResource(R.string.allow), onAllow, icon = TwIcons.Camera)
        Spacer(Modifier.height(16.dp))
        StickerButton(stringResource(R.string.pick_gallery), onGallery, color = Paper, icon = TwIcons.Gallery)
    }
}

@Composable
fun PhotoPreviewScreen(photo: Bitmap, onBack: () -> Unit, onRetake: () -> Unit, onSend: () -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
    ) {
        TwTopBar(stringResource(R.string.mess_check), onBack, backLabel = stringResource(R.string.back)) {
            StepDots(2, Orange)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            // a polaroid, a little crooked
            Box(
                Modifier.fillMaxWidth(0.9f).rotate(-2.5f).sticker(RoundedCornerShape(10.dp), Paper, 6.dp)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp)
            ) {
                Image(
                    image, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.86f).clip(RoundedCornerShape(4.dp))
                        .background(Line).border(2.dp, Ink, RoundedCornerShape(4.dp)),
                )
            }
            Spacer(Modifier.height(30.dp))
            Text(stringResource(R.string.preview_title), style = TwType.title)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.preview_body), style = TwType.body,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            StickerButton(stringResource(R.string.send_to_ai), onSend, icon = TwIcons.Sparkle)
            Spacer(Modifier.height(16.dp))
            StickerButton(stringResource(R.string.retake), onRetake, color = Paper, icon = TwIcons.Retry)
        }
    }
}

// -- camera plumbing -------------------------------------------------------

fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { c ->
    val f = ProcessCameraProvider.getInstance(context)
    f.addListener({ c.resume(f.get()) }, ContextCompat.getMainExecutor(context))
}

/** The frame the right way up, long side at most PHOTO_SIDE. */
private fun ImageProxy.upright(): Bitmap {
    val src = toBitmap()
    val s = minOf(1f, PHOTO_SIDE.toFloat() / maxOf(src.width, src.height))
    val m = Matrix().apply { postScale(s, s); postRotate(imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun decode(context: Context, uri: Uri): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = minOf(1f, PHOTO_SIDE.toFloat() / maxOf(info.size.width, info.size.height))
        d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
    }
} catch (e: Exception) {
    null
}

package com.agniv.hisaab.feature.expenses.scan

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Takes bill photos straight from the live preview (CameraX). Only the still-photo use case is bound, so it stays
 * light on 2 GB phones; the camera closes by itself when the screen leaves (lifecycle-bound).
 */
internal class LiveCameraState(
    val controller: LifecycleCameraController,
) {
    /** Saves one photo to [file]; [onDone] gets its content Uri, or null when the camera failed. */
    fun capture(
        context: Context,
        file: File,
        onDone: (Uri?) -> Unit,
    ) {
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    onDone(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
                }

                override fun onError(exception: ImageCaptureException) = onDone(null)
            },
        )
    }
}

@Composable
internal fun rememberLiveCamera(): LiveCameraState {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller =
        remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
                imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            }
        }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    return remember(controller) { LiveCameraState(controller) }
}

@Composable
internal fun LivePreview(
    state: LiveCameraState,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // COMPATIBLE (TextureView) clips to the rounded frame correctly inside Compose.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                controller = state.controller
            }
        },
        modifier = modifier,
    )
}

package com.example.connect.ui.theme.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.connect.ui.theme.Charcoal900
import com.example.connect.ui.theme.ElectricCyan
import com.example.connect.ui.theme.TextPrimary
import com.example.connect.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors


private const val TAG = "ScanScreen"
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(onDeviceScanned: (String) -> Unit){
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted){
            cameraPermission.launchPermissionRequest()
        }
    }
    when {
        cameraPermission.status.isGranted -> {
            ScannerContent(onDeviceScanned = onDeviceScanned)
        }
        cameraPermission.status.shouldShowRationale -> {
            PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
        }
        else -> {
            PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
        }
    }
}

@Composable
private fun ScannerContent(onDeviceScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Prevent firing onDeviceScanned more than once
    var scanned by remember { mutableStateOf(false) }
    var scannedId by remember { mutableStateOf<String?>(null) }
    //    Animate sweep line top -> bottom , looping
    val sweepAnim = rememberInfiniteTransition(label = "sweep")
    val sweepFraction by sweepAnim.animateFloat(
        initialValue   = 0f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepFraction"
    )
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val previewView = PreviewView(ctx)
                val executor    = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                processImageProxy(imageProxy) { deviceId ->
                                    if (!scanned) {
                                        scanned  = true
                                        scannedId = deviceId
                                        onDeviceScanned(deviceId)
                                    }
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // ── Dark overlay + reticle ────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val reticleSize = size.width * 0.65f
            val left   = (size.width  - reticleSize) / 2f
            val top    = (size.height - reticleSize) / 2f

            // Semi-transparent dark overlay with cutout
            drawRect(color = Color(0xAA000000))
            drawRoundRect(
                color        = Color.Transparent,
                topLeft      = Offset(left, top),
                size         = Size(reticleSize, reticleSize),
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode    = BlendMode.Clear
            )

            // Cyan border around reticle
            drawRoundRect(
                color        = ElectricCyan,
                topLeft      = Offset(left, top),
                size         = Size(reticleSize, reticleSize),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style        = Stroke(width = 2.dp.toPx())
            )

            // Sweep line inside reticle
            val sweepY = top + sweepFraction * reticleSize
            drawLine(
                color       = ElectricCyan.copy(alpha = 0.7f),
                start       = Offset(left + 8.dp.toPx(), sweepY),
                end         = Offset(left + reticleSize - 8.dp.toPx(), sweepY),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // ── Labels ────────────────────────────────────────────────────────
        Column(
            modifier              = Modifier.fillMaxSize(),
            verticalArrangement   = Arrangement.SpaceBetween,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text      = "Scan Device QR Code",
                color     = TextPrimary,
                fontSize  = 20.sp,
                modifier  = Modifier.padding(top = 64.dp)
            )
            // Show decoded ID briefly before navigation
            val id = scannedId
            if (id != null) {
                Text(
                    text       = id,
                    color      = ElectricCyan,
                    fontSize   = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.padding(bottom = 48.dp)
                )
            } else {
                Text(
                    text     = "Point at the QR code shown in your terminal",
                    color    = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
                )
            }
        }
    }
}
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    onFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image   = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT }
                    ?.rawValue
                    ?.let { onFound(it) }
            }
            .addOnFailureListener { Log.e(TAG, "Barcode scan failed", it) }
            .addOnCompleteListener { imageProxy.close() }
    } else {
        imageProxy.close()
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier            = Modifier
            .fillMaxSize()
            .background(Charcoal900),
        contentAlignment    = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is required to scan QR codes",
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onRequest) {
                Text("Grant Permission")
            }
        }
    }
}
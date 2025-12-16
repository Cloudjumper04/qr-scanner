package com.plcoding.qrcodescannercompose

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.plcoding.qrcodescannercompose.ui.theme.QrCodeScannerComposeTheme
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.Brush
import androidx.camera.core.Camera
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff

import androidx.compose.material.Icon
import androidx.compose.material.IconButton

import androidx.compose.ui.graphics.Color




// ---------- helpers ----------

fun String.isUrl(): Boolean =
    Patterns.WEB_URL.matcher(this).matches()

// ---------- activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            QrCodeScannerComposeTheme {

                var code by remember { mutableStateOf<String?>(null) }
                var camera by remember { mutableStateOf<Camera?>(null) }
                var torchOn by remember { mutableStateOf(false) }


                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val cameraProviderFuture = remember {
                    ProcessCameraProvider.getInstance(context)
                }

                var hasCamPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted -> hasCamPermission = granted }
                )

                LaunchedEffect(Unit) {
                    launcher.launch(Manifest.permission.CAMERA)
                }

                val view = LocalView.current
                val window = (view.context as Activity).window

                SideEffect {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    WindowInsetsControllerCompat(window, view)
                        .isAppearanceLightStatusBars = false
                }

                Box(modifier = Modifier.fillMaxSize()) {

                    // ---------- camera preview ----------
                    if (hasCamPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)

                                val preview = Preview.Builder().build()
                                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                                preview.setSurfaceProvider(previewView.surfaceProvider)

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(
                                    ContextCompat.getMainExecutor(ctx),
                                    QrCodeAnalyzer { result ->
                                        if (code == null) {
                                            code = result
                                        }
                                    }
                                )

                                try {
                                    camera = cameraProviderFuture.get().bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // ---------- scan frame ----------
                    ScannerOverlay()

                    // ---------- status bar blend --------
                    TopGradientOverlay()

                    // ---------- overlay title ----------
                    Text(
                        text = "QR Scanner",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp)
                    )

                    // ---------- torch button ----------

                    IconButton(
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(32.dp)
                    ) {
                        Icon(
                            imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle flashlight",
                            tint = Color.White
                        )
                    }


                    // ---------- result dialog ----------
                    code?.let {
                        ScanResultDialog(
                            result = it,
                            onDismiss = { code = null }
                        )
                    }
                }
            }
        }
    }
}

// ---------- overlay ----------

@Composable
fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val overlayColor = Color.Black.copy(alpha = 0.5f)
        drawRect(color = overlayColor)

        val rectSize = size.width * 0.7f
        val left = (size.width - rectSize) / 2
        val top = (size.height - rectSize) / 2

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(rectSize, rectSize),
            cornerRadius = CornerRadius(24f, 24f),
            blendMode = BlendMode.Clear
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(rectSize, rectSize),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 4f)
        )
    }
}

@Composable
fun TopGradientOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
    )
}


// ---------- result dialog ----------

@Composable
fun ScanResultDialog(
    result: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan result") },
        text = {
            SelectionContainer {
                Text(result)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(result))
                }) {
                    Text("Copy")
                }

                if (result.isUrl()) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(result))
                        )
                    }) {
                        Text("Open")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Scan again")
            }
        }
    )
}

package com.openbaby.monitor.ui.components

import android.view.SurfaceHolder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.library.view.OpenGlView

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceReady: (OpenGlView) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val context = LocalContext.current
    val openGlView = remember { OpenGlView(context) }

    DisposableEffect(Unit) {
        onDispose {
            onSurfaceDestroyed()
        }
    }

    AndroidView(
        factory = {
            openGlView.apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceReady(this@apply)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        // Handle surface size changes if needed
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceDestroyed()
                    }
                })
            }
        },
        modifier = modifier
    )
}

/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.additions

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

const val BACKGROUND_TYPE_NONE = 0
const val BACKGROUND_TYPE_IMAGE = 1
const val BACKGROUND_TYPE_VIDEO = 2

private const val MAX_IMAGE_SIDE = 1600

private fun decodeImage(file: File): Drawable? {
    return try {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also handles animated GIF / WebP.
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > MAX_IMAGE_SIDE) decoder.setTargetSampleSize(side / MAX_IMAGE_SIDE)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = max(1, max(bounds.outWidth, bounds.outHeight) / MAX_IMAGE_SIDE)
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.let { BitmapDrawable(null as android.content.res.Resources?, it) }
        }
    } catch (_: Throwable) {
        null
    }
}

/** Image / GIF / video background drawn behind the keys, with dim and tint overlays. */
@Composable
fun KeyboardBackgroundLayer(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val type by prefs.additions.backgroundType.observeAsState()
    val path by prefs.additions.backgroundPath.observeAsState()
    val dim by prefs.additions.backgroundDim.observeAsState()
    val tint by prefs.additions.backgroundTint.observeAsState()
    val tintAlpha by prefs.additions.backgroundTintAlpha.observeAsState()

    if (type == BACKGROUND_TYPE_NONE || path.isBlank()) return
    val file = remember(path) { File(path) }
    if (!file.exists()) return

    Box(modifier = modifier) {
        when (type) {
            BACKGROUND_TYPE_IMAGE -> ImageBackground(file, Modifier.matchParentSize())
            BACKGROUND_TYPE_VIDEO -> VideoBackground(file, Modifier.matchParentSize())
        }
        if (dim > 0) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim.coerceIn(0, 100) / 100f)))
        }
        if (tint != 0 && tintAlpha > 0) {
            Box(Modifier.matchParentSize().background(Color(tint).copy(alpha = tintAlpha.coerceIn(0, 100) / 100f)))
        }
    }
}

@Composable
private fun ImageBackground(file: File, modifier: Modifier) {
    val drawable by produceState<Drawable?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { decodeImage(file) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { view ->
            view.setImageDrawable(drawable)
            if (Build.VERSION.SDK_INT >= 28) {
                (drawable as? AnimatedImageDrawable)?.apply {
                    repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    start()
                }
            }
        },
    )
}

private class VideoHolder {
    var player: MediaPlayer? = null
    fun release() {
        try { player?.release() } catch (_: Throwable) { }
        player = null
    }
}

@Composable
private fun VideoBackground(file: File, modifier: Modifier) {
    val holder = remember(file) { VideoHolder() }
    DisposableEffect(file) { onDispose { holder.release() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        holder.release()
                        try {
                            val surface = Surface(st)
                            holder.player = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                setSurface(surface)
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnVideoSizeChangedListener { _, vw, vh ->
                                    if (vw > 0 && vh > 0) {
                                        val viewW = width.toFloat()
                                        val viewH = height.toFloat()
                                        val videoAspect = vw.toFloat() / vh
                                        val viewAspect = viewW / viewH
                                        val sx = if (videoAspect > viewAspect) videoAspect / viewAspect else 1f
                                        val sy = if (videoAspect > viewAspect) 1f else viewAspect / videoAspect
                                        setTransform(Matrix().apply { setScale(sx, sy, viewW / 2f, viewH / 2f) })
                                    }
                                }
                                setOnPreparedListener { it.start() }
                                prepareAsync()
                            }
                        } catch (_: Throwable) {
                            holder.release()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        holder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

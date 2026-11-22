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

package dev.patrickgold.florisboard.app.settings.additions

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.ime.additions.BACKGROUND_TYPE_IMAGE
import dev.patrickgold.florisboard.ime.additions.BACKGROUND_TYPE_NONE
import dev.patrickgold.florisboard.ime.additions.BACKGROUND_TYPE_VIDEO
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes
import java.io.File

private val TintPresets = listOf(
    0x00000000, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
    0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFFFFFFF.toInt(),
)

private fun copyBackground(context: Context, uri: Uri): Pair<Int, String>? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri).orEmpty()
    val type = if (mime.startsWith("video/")) BACKGROUND_TYPE_VIDEO else BACKGROUND_TYPE_IMAGE
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        ?: if (type == BACKGROUND_TYPE_VIDEO) "mp4" else "img"
    val dir = File(context.filesDir, "additions").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    // new name per import so the layer reloads
    val target = File(dir, "background_${System.currentTimeMillis()}.$ext")
    return try {
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        type to target.absolutePath
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun AdditionsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__additions__title)
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTintDialog by remember { mutableStateOf(false) }
    val backgroundType by prefs.additions.backgroundType.observeAsState()
    val tint by prefs.additions.backgroundTint.observeAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { copyBackground(context, uri) }
                if (result != null) {
                    prefs.additions.backgroundPath.set(result.second)
                    prefs.additions.backgroundType.set(result.first)
                }
            }
        }
    }

    content {
        PreferenceGroup(title = stringRes(R.string.settings__additions__group_tools)) {
            Preference(
                title = stringRes(R.string.settings__expander__title),
                summary = stringRes(R.string.settings__additions__expander_summary),
                onClick = { navController.navigate(Routes.Settings.Expander) },
            )
            SwitchPreference(
                prefs.additions.findReplaceRegex,
                title = stringRes(R.string.settings__additions__regex),
                summary = stringRes(R.string.settings__additions__regex_summary),
            )
            Preference(
                title = stringRes(R.string.settings__custom_actions__title),
                summary = stringRes(R.string.settings__custom_actions__summary),
                onClick = { navController.navigate(Routes.Settings.CustomActions) },
            )
            SwitchPreference(
                prefs.additions.calcAutoOnEquals,
                title = stringRes(R.string.settings__additions__calc_auto),
                summary = stringRes(R.string.settings__additions__calc_auto_summary),
            )
            Preference(
                title = stringRes(R.string.settings__additions__toolbar_tip),
                summary = stringRes(R.string.settings__additions__toolbar_tip_summary),
                onClick = { navController.navigate(Routes.Settings.Smartbar) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__additions__group_layout)) {
            Preference(
                title = stringRes(R.string.settings__layout_editor__title),
                summary = stringRes(R.string.settings__layout_editor__summary),
                onClick = { navController.navigate(Routes.Settings.LayoutEditor) },
            )
            DialogSliderPreference(
                prefs.keyboard.deleteKeyWidthPercent,
                title = stringRes(R.string.pref__keyboard__delete_key_width__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 40,
                max = 150,
                stepIncrement = 5,
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__additions__group_emoji)) {
            DialogSliderPreference(
                prefs.emoji.sizePercent,
                title = stringRes(R.string.prefs__media__emoji_size),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 60,
                max = 250,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                prefs.emoji.gridColumns,
                title = stringRes(R.string.prefs__media__emoji_grid_columns),
                valueLabel = {
                    if (it == 0) stringRes(R.string.prefs__media__emoji_grid_columns__auto) else it.toString()
                },
                min = 0,
                max = 12,
                stepIncrement = 1,
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__additions__group_background)) {
            Preference(
                title = stringRes(R.string.settings__additions__background_pick),
                summary = stringRes(
                    when (backgroundType) {
                        BACKGROUND_TYPE_IMAGE -> R.string.settings__additions__background_image
                        BACKGROUND_TYPE_VIDEO -> R.string.settings__additions__background_video
                        else -> R.string.settings__additions__background_none
                    }
                ),
                onClick = { picker.launch(arrayOf("image/*", "video/*")) },
            )
            if (backgroundType != BACKGROUND_TYPE_NONE) {
                Preference(
                    title = stringRes(R.string.settings__additions__background_remove),
                    onClick = {
                        scope.launch {
                            prefs.additions.backgroundType.set(BACKGROUND_TYPE_NONE)
                            prefs.additions.backgroundPath.set("")
                            withContext(Dispatchers.IO) {
                                File(context.filesDir, "additions").listFiles()?.forEach { it.delete() }
                            }
                        }
                    },
                )
            }
            DialogSliderPreference(
                prefs.additions.backgroundDim,
                title = stringRes(R.string.settings__additions__background_dim),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 0,
                max = 90,
                stepIncrement = 5,
            )
            Preference(
                title = stringRes(R.string.settings__additions__background_tint),
                summary = if (tint == 0) "-" else "#" + Integer.toHexString(tint).uppercase().takeLast(6),
                onClick = { showTintDialog = true },
            )
            DialogSliderPreference(
                prefs.additions.backgroundTintAlpha,
                title = stringRes(R.string.settings__additions__background_tint_alpha),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 0,
                max = 100,
                stepIncrement = 5,
            )
            Preference(
                title = stringRes(R.string.settings__additions__theme_colors),
                summary = stringRes(R.string.settings__additions__theme_colors_summary),
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
        }

        if (showTintDialog) {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__additions__background_tint),
                confirmLabel = stringRes(R.string.action__ok),
                onConfirm = { showTintDialog = false },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showTintDialog = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (c in TintPresets) {
                        Row(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (c == 0) Color.Transparent else Color(c))
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable {
                                    scope.launch { prefs.additions.backgroundTint.set(c) }
                                    showTintDialog = false
                                },
                        ) {
                            if (c == 0) Text("∅", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

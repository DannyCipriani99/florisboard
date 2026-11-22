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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.additions.LayoutEdit
import dev.patrickgold.florisboard.ime.additions.LayoutEditList
import dev.patrickgold.florisboard.ime.additions.LayoutEditOp
import dev.patrickgold.florisboard.ime.additions.LayoutEditor
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private data class KeyRef(val row: Int, val col: Int, val label: String)

private fun parsePopups(text: String): List<String> =
    text.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }

@Composable
fun LayoutEditorScreen() = FlorisScreen {
    title = stringRes(R.string.settings__layout_editor__title)
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val scope = rememberCoroutineScope()
    val editList by prefs.additions.layoutEdits.observeAsState()
    var mode by remember { mutableStateOf(KeyboardMode.CHARACTERS) }
    var selected by remember { mutableStateOf<KeyRef?>(null) }
    var adding by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val keyboard by produceState<TextKeyboard?>(initialValue = null, mode, editList) {
        value = runCatching {
            keyboardManager.layoutManager.computeKeyboardAsync(mode, subtypeManager.activeSubtype).await()
        }.getOrNull()
    }

    fun addEdit(edit: LayoutEdit) {
        scope.launch { prefs.additions.layoutEdits.set(LayoutEditList(editList.edits + edit)) }
    }

    content {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringRes(R.string.settings__layout_editor__hint), style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in LayoutEditor.EditableModes) {
                    val label = stringRes(
                        when (m) {
                            KeyboardMode.CHARACTERS -> R.string.settings__layout_editor__mode_letters
                            KeyboardMode.SYMBOLS -> R.string.settings__layout_editor__mode_symbols
                            else -> R.string.settings__layout_editor__mode_symbols2
                        }
                    )
                    if (m == mode) {
                        Button(onClick = { mode = m }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { mode = m }) { Text(label) }
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                keyboard?.arrangement?.forEachIndexed { r, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEachIndexed { c, key ->
                            val label = runCatching { key.data.compute(DefaultComputingEvaluator)?.asString(true) }
                                .getOrNull()?.takeIf { it.isNotBlank() } ?: "•"
                            OutlinedButton(
                                onClick = { selected = KeyRef(r, c, label) },
                                contentPadding = ButtonDefaults.ContentPadding,
                            ) { Text(label, maxLines = 1) }
                        }
                        Button(onClick = { adding = r to row.size }) { Text("+") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = editList.edits.isNotEmpty(),
                    onClick = {
                        scope.launch { prefs.additions.layoutEdits.set(LayoutEditList(editList.edits.dropLast(1))) }
                    },
                ) { Text(stringRes(R.string.settings__layout_editor__undo)) }
                TextButton(
                    enabled = editList.edits.isNotEmpty(),
                    onClick = { scope.launch { prefs.additions.layoutEdits.set(LayoutEditList()) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringRes(R.string.settings__layout_editor__reset)) }
            }
        }

        selected?.let { ref ->
            var popups by remember(ref) { mutableStateOf("") }
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__layout_editor__key_title, "key" to ref.label),
                confirmLabel = stringRes(R.string.action__save),
                onConfirm = {
                    if (popups.isNotBlank()) {
                        addEdit(LayoutEdit(mode.name, LayoutEditOp.POPUPS, ref.row, ref.col, popups = parsePopups(popups)))
                    }
                    selected = null
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { selected = null },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val moves = listOf(
                                "◀" to (ref.row to ref.col - 1),
                                "▶" to (ref.row to ref.col + 1),
                                "▲" to (ref.row - 1 to ref.col),
                                "▼" to (ref.row + 1 to ref.col),
                            )
                            for ((symbol, dest) in moves) {
                                OutlinedButton(
                                    enabled = dest.second >= 0 && dest.first >= 0,
                                    onClick = {
                                        addEdit(
                                            LayoutEdit(
                                                mode.name, LayoutEditOp.MOVE, ref.row, ref.col,
                                                toRow = dest.first, toCol = dest.second,
                                            )
                                        )
                                        selected = null
                                    },
                                ) { Text(symbol) }
                            }
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = popups,
                        onValueChange = { popups = it },
                        label = { Text(stringRes(R.string.settings__layout_editor__popups)) },
                        supportingText = { Text(stringRes(R.string.settings__layout_editor__popups_hint)) },
                    )
                    TextButton(
                        onClick = {
                            addEdit(LayoutEdit(mode.name, LayoutEditOp.DELETE, ref.row, ref.col))
                            selected = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringRes(R.string.action__delete)) }
                }
            }
        }

        adding?.let { (row, col) ->
            var text by remember(row, col) { mutableStateOf("") }
            var popups by remember(row, col) { mutableStateOf("") }
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__layout_editor__add_title),
                confirmLabel = stringRes(R.string.action__save),
                onConfirm = {
                    if (text.isEmpty()) return@JetPrefAlertDialog
                    addEdit(
                        LayoutEdit(
                            mode.name, LayoutEditOp.INSERT, row, col,
                            text = text, popups = parsePopups(popups),
                        )
                    )
                    adding = null
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { adding = null },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text(stringRes(R.string.settings__layout_editor__add_text)) },
                        supportingText = { Text(stringRes(R.string.settings__layout_editor__add_text_hint)) },
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = popups,
                        onValueChange = { popups = it },
                        label = { Text(stringRes(R.string.settings__layout_editor__popups)) },
                    )
                }
            }
        }
    }
}

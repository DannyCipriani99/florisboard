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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
private fun KeyBox(label: String, extra: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .widthIn(min = 42.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .border(1.dp, if (selected) scheme.primary else scheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        )
        if (extra > 0) {
            Text(
                "⋯$extra",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    fun labelOf(key: dev.patrickgold.florisboard.ime.text.keyboard.TextKey): String =
        runCatching { key.data.compute(DefaultComputingEvaluator)?.asString(true) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "•"

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
                        Button(onClick = { mode = m; selected = null }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { mode = m; selected = null }) { Text(label) }
                    }
                }
            }

            val kb = keyboard
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                kb?.arrangement?.forEachIndexed { r, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEachIndexed { c, key ->
                            KeyBox(
                                label = labelOf(key),
                                extra = LayoutEditor.currentPopups(key, kb).size,
                                selected = selected?.row == r && selected?.col == c,
                            ) { selected = KeyRef(r, c, labelOf(key)) }
                        }
                        Button(onClick = { adding = r to row.size }) { Text("+") }
                    }
                }
            }

            // ---- selected key: move / delete / popups -----------------------------------------------------------
            val ref = selected
            val selKey = if (ref != null && kb != null) kb.arrangement.getOrNull(ref.row)?.getOrNull(ref.col) else null
            if (ref != null && kb != null && selKey != null) {
                val popupList = remember(ref, editList) {
                    mutableStateListOf<String>().apply { addAll(LayoutEditor.currentPopups(selKey, kb)) }
                }
                var popupSel by remember(ref, editList) { mutableStateOf<Int?>(null) }
                var newPopup by remember(ref, editList) { mutableStateOf("") }
                val rowCount = kb.arrangement.size
                val rowSize = kb.arrangement[ref.row].size

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringRes(R.string.settings__layout_editor__key_title, "key" to ref.label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            val moves = listOf(
                                Triple("◀", ref.col > 0, ref.row to ref.col - 1),
                                Triple("▶", ref.col < rowSize - 1, ref.row to ref.col + 1),
                                Triple("▲", ref.row > 0, ref.row - 1 to ref.col),
                                Triple("▼", ref.row < rowCount - 1, ref.row + 1 to ref.col),
                            )
                            for ((symbol, enabled, dest) in moves) {
                                OutlinedButton(
                                    enabled = enabled,
                                    onClick = {
                                        addEdit(
                                            LayoutEdit(
                                                mode.name, LayoutEditOp.MOVE, ref.row, ref.col,
                                                toRow = dest.first, toCol = dest.second,
                                            )
                                        )
                                        selected = KeyRef(dest.first, dest.second, ref.label)
                                    },
                                ) { Text(symbol) }
                            }
                            TextButton(
                                onClick = {
                                    addEdit(LayoutEdit(mode.name, LayoutEditOp.DELETE, ref.row, ref.col))
                                    selected = null
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringRes(R.string.action__delete)) }
                        }
                    }

                    Text(stringRes(R.string.settings__layout_editor__popups), style = MaterialTheme.typography.titleSmall)
                    if (popupList.isEmpty()) {
                        Text(stringRes(R.string.settings__layout_editor__no_popups))
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            popupList.forEachIndexed { i, p ->
                                KeyBox(label = p, extra = 0, selected = popupSel == i) { popupSel = i }
                            }
                        }
                        popupSel?.let { i ->
                            if (i in popupList.indices) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(enabled = i > 0, onClick = {
                                        val t = popupList.removeAt(i); popupList.add(i - 1, t); popupSel = i - 1
                                    }) { Text("◀") }
                                    OutlinedButton(enabled = i < popupList.size - 1, onClick = {
                                        val t = popupList.removeAt(i); popupList.add(i + 1, t); popupSel = i + 1
                                    }) { Text("▶") }
                                    TextButton(
                                        onClick = { popupList.removeAt(i); popupSel = null },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) { Text(stringRes(R.string.action__delete)) }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = newPopup,
                            onValueChange = { newPopup = it },
                            singleLine = true,
                            label = { Text(stringRes(R.string.settings__layout_editor__add_popup)) },
                        )
                        OutlinedButton(onClick = {
                            parsePopups(newPopup).forEach { popupList.add(it) }
                            newPopup = ""
                        }) { Text("+") }
                    }
                    Button(onClick = {
                        addEdit(LayoutEdit(mode.name, LayoutEditOp.POPUPS, ref.row, ref.col, popups = popupList.toList()))
                    }) { Text(stringRes(R.string.settings__layout_editor__apply_popups)) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = editList.edits.isNotEmpty(),
                    onClick = {
                        scope.launch { prefs.additions.layoutEdits.set(LayoutEditList(editList.edits.dropLast(1))) }
                        selected = null
                    },
                ) { Text(stringRes(R.string.settings__layout_editor__undo)) }
                TextButton(
                    enabled = editList.edits.isNotEmpty(),
                    onClick = {
                        scope.launch { prefs.additions.layoutEdits.set(LayoutEditList()) }
                        selected = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringRes(R.string.settings__layout_editor__reset)) }
            }
        }

        adding?.let { (row, col) ->
            var text by remember(row, col) { mutableStateOf("") }
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__layout_editor__add_title),
                confirmLabel = stringRes(R.string.action__save),
                onConfirm = {
                    if (text.isEmpty()) return@JetPrefAlertDialog
                    addEdit(LayoutEdit(mode.name, LayoutEditOp.INSERT, row, col, text = text))
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
                    Text(stringRes(R.string.settings__layout_editor__add_special), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (sk in LayoutEditor.SpecialKeys) {
                            OutlinedButton(onClick = {
                                addEdit(LayoutEdit(mode.name, LayoutEditOp.INSERT, row, col, keyCode = sk.data.code))
                                adding = null
                            }) { Text(sk.label) }
                        }
                    }
                }
            }
        }
    }
}

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

import dev.patrickgold.florisboard.ime.keyboard.AbstractKeyData
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.popup.PopupSet
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.AutoTextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.MultiTextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.tryOrNull

enum class LayoutEditOp { DELETE, MOVE, INSERT, POPUPS }

/**
 * One manual change of the keyboard layout. Edits are applied in order on top of the computed layout of [mode],
 * so every position refers to the layout state after all previous edits.
 */
@Serializable
data class LayoutEdit(
    /** Name of a [KeyboardMode] (CHARACTERS, SYMBOLS or SYMBOLS2). */
    val mode: String,
    val op: LayoutEditOp,
    val row: Int = 0,
    val col: Int = 0,
    val toRow: Int = 0,
    val toCol: Int = 0,
    /** Text of the inserted key (INSERT). One character = normal key, several = multi-character key such as "00". */
    val text: String = "",
    /** Custom long-press popup entries (POPUPS and INSERT). */
    val popups: List<String> = emptyList(),
    /** Key code of a special key (Shift, Alt, Ctrl, Tab, ...) to insert when [text] is empty (INSERT). */
    val keyCode: Int = 0,
)

/** Popup set created from manual edits. Replaces the language's default popups of a key instead of extending them. */
class CustomPopupSet(
    main: AbstractKeyData? = null,
    relevant: List<AbstractKeyData> = emptyList(),
) : PopupSet<AbstractKeyData>(main, relevant)

class SpecialKey(val label: String, val data: TextKeyData)

@Serializable
data class LayoutEditList(
    val edits: List<LayoutEdit> = emptyList(),
) {
    object Serializer : PreferenceSerializer<LayoutEditList> {
        override fun serialize(value: LayoutEditList): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): LayoutEditList {
            return tryOrNull { Json.decodeFromString<LayoutEditList>(value) } ?: LayoutEditList()
        }
    }
}

object LayoutEditor {
    private fun special(label: String, type: KeyType, code: Int, text: String) =
        SpecialKey(label, TextKeyData(type = type, code = code, label = text))

    /** Special keys that can be added to the keyboard from the layout editor. */
    val SpecialKeys: List<SpecialKey> = listOf(
        SpecialKey("Shift", TextKeyData.SHIFT),
        SpecialKey("⌫", TextKeyData.DELETE),
        special("Enter", KeyType.ENTER_EDITING, KeyCode.ENTER, "enter"),
        SpecialKey("Space", TextKeyData.SPACE),
        special("Ctrl", KeyType.MODIFIER, KeyCode.CTRL, "Ctrl"),
        special("Alt", KeyType.MODIFIER, KeyCode.ALT, "Alt"),
        special("Tab", KeyType.FUNCTION, KeyCode.TAB, "Tab"),
        special("Esc", KeyType.FUNCTION, KeyCode.ESCAPE, "Esc"),
        SpecialKey("←", TextKeyData.ARROW_LEFT),
        SpecialKey("→", TextKeyData.ARROW_RIGHT),
        SpecialKey("↑", TextKeyData.ARROW_UP),
        SpecialKey("↓", TextKeyData.ARROW_DOWN),
        SpecialKey("Copy", TextKeyData.CLIPBOARD_COPY),
        SpecialKey("Paste", TextKeyData.CLIPBOARD_PASTE),
        SpecialKey("Cut", TextKeyData.CLIPBOARD_CUT),
        SpecialKey("Select all", TextKeyData.CLIPBOARD_SELECT_ALL),
        SpecialKey("Undo", TextKeyData.UNDO),
        SpecialKey("Redo", TextKeyData.REDO),
        SpecialKey("🌐", TextKeyData.LANGUAGE_SWITCH),
        SpecialKey("?123", TextKeyData.VIEW_SYMBOLS),
        SpecialKey("ABC", TextKeyData.VIEW_CHARACTERS),
    )

    fun specialKeyData(code: Int): TextKeyData? = SpecialKeys.firstOrNull { it.data.code == code }?.data

    /** Entries (characters) of the popups a key currently shows on long press, for display in the editor. */
    fun currentPopups(key: TextKey, keyboard: TextKeyboard): List<String> {
        fun entries(set: PopupSet<AbstractKeyData>?): List<AbstractKeyData> =
            if (set == null) emptyList() else listOfNotNull(set.main) + set.relevant
        val own = runCatching { key.data.compute(DefaultComputingEvaluator)?.popup }.getOrNull()
        val sets = mutableListOf<PopupSet<AbstractKeyData>?>(own)
        if (own !is CustomPopupSet) {
            val label = runCatching { key.data.compute(DefaultComputingEvaluator)?.label }.getOrNull()?.lowercase()
            if (label != null) {
                sets += keyboard.extendedPopupMapping?.get(KeyVariation.ALL)?.get(label)
                sets += keyboard.extendedPopupMappingDefault?.get(KeyVariation.ALL)?.get(label)
            }
        }
        return sets.flatMap { entries(it) }
            .mapNotNull { runCatching { it.compute(DefaultComputingEvaluator)?.asString(true) }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val EditableModes = listOf(KeyboardMode.CHARACTERS, KeyboardMode.SYMBOLS, KeyboardMode.SYMBOLS2)

    fun createKeyData(text: String, popups: List<String> = emptyList()): TextKeyData? {
        if (text.isEmpty()) return null
        val cps = text.codePoints().toArray()
        val type = if (text.all { it.isDigit() }) KeyType.NUMERIC else KeyType.CHARACTER
        val set = popupSetOf(popups)
        return if (cps.size == 1) {
            TextKeyData(type = type, code = cps[0], label = text, popup = set)
        } else {
            null
        }
    }

    private fun createAnyKeyData(text: String, popups: List<String>): AbstractKeyData? {
        if (text.isEmpty()) return null
        val cps = text.codePoints().toArray()
        if (cps.size == 1) return createKeyData(text, popups)
        val type = if (text.all { it.isDigit() }) KeyType.NUMERIC else KeyType.CHARACTER
        return MultiTextKeyData(type = type, codePoints = cps, label = text, popup = popupSetOf(popups))
    }

    private fun popupSetOf(popups: List<String>): PopupSet<AbstractKeyData>? {
        val items = popups.mapNotNull { createAnyKeyData(it, emptyList()) }
        if (items.isEmpty()) return null
        return CustomPopupSet(main = null, relevant = items)
    }

    private fun withPopups(data: AbstractKeyData, popups: List<String>): AbstractKeyData {
        val set: PopupSet<AbstractKeyData> =
            CustomPopupSet(null, popups.mapNotNull { createAnyKeyData(it, emptyList()) })
        return when (data) {
            is TextKeyData -> data.copy(popup = set)
            is AutoTextKeyData -> AutoTextKeyData(data.type, data.code, data.label, data.groupId, set)
            is MultiTextKeyData -> MultiTextKeyData(data.type, data.codePoints, data.label, data.groupId, set)
            else -> {
                val computed = data.compute(DefaultComputingEvaluator)
                if (computed != null) {
                    TextKeyData(computed.type, computed.code, computed.label, computed.groupId, set)
                } else {
                    data
                }
            }
        }
    }

    /** Applies all edits of [mode] to [arrangement] and returns the new arrangement. */
    fun apply(mode: KeyboardMode, arrangement: List<Array<TextKey>>, edits: List<LayoutEdit>): List<Array<TextKey>> {
        val relevant = edits.filter { it.mode == mode.name }
        if (relevant.isEmpty()) return arrangement
        val rows = arrangement.map { it.toMutableList() }.toMutableList()
        for (e in relevant) {
            when (e.op) {
                LayoutEditOp.DELETE -> {
                    rows.getOrNull(e.row)?.let { if (e.col in it.indices) it.removeAt(e.col) }
                }
                LayoutEditOp.MOVE -> {
                    val source = rows.getOrNull(e.row) ?: continue
                    if (e.col !in source.indices) continue
                    val target = rows.getOrNull(e.toRow) ?: continue
                    val key = source.removeAt(e.col)
                    target.add(e.toCol.coerceIn(0, target.size), key)
                }
                LayoutEditOp.INSERT -> {
                    val row = rows.getOrNull(e.row) ?: continue
                    val data = (if (e.text.isEmpty()) specialKeyData(e.keyCode) else createAnyKeyData(e.text, e.popups))
                        ?: continue
                    row.add(e.col.coerceIn(0, row.size), TextKey(data))
                }
                LayoutEditOp.POPUPS -> {
                    val row = rows.getOrNull(e.row) ?: continue
                    val old = row.getOrNull(e.col) ?: continue
                    val replacement = TextKey(withPopups(old.data, e.popups))
                    replacement.computedSymbolHint = old.computedSymbolHint
                    replacement.computedNumberHint = old.computedNumberHint
                    row[e.col] = replacement
                }
            }
        }
        return rows.filter { it.isNotEmpty() }.map { it.toTypedArray() }
    }
}

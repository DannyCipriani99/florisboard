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

package dev.patrickgold.florisboard.ime.expander

import dev.patrickgold.florisboard.ime.additions.ActionTarget
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.tryOrNull

/**
 * A single text expander rule: typing [abbreviation] is replaced by [expansion].
 *
 * Supported placeholders inside [expansion]: `{clipboard}` is replaced by the current clipboard text.
 */
@Serializable
data class TextExpansion(
    val abbreviation: String,
    val expansion: String = "",
    /** If true the replacement happens immediately when the abbreviation is typed, else when space is typed. */
    val instant: Boolean = false,
    val caseSensitive: Boolean = true,
    /** If true a broadcast intent is sent when this rule fires (for Tasker / MacroDroid). */
    val broadcastEnabled: Boolean = false,
    /** Custom intent action for the broadcast; blank = [TextExpander.DEFAULT_BROADCAST_ACTION]. */
    val broadcastAction: String = "",
    /** Optional app / activity / link / intent that is started when this rule fires. */
    val target: ActionTarget = ActionTarget(),
)

@Serializable
data class TextExpansionList(
    val rules: List<TextExpansion> = emptyList(),
) {
    object Serializer : PreferenceSerializer<TextExpansionList> {
        override fun serialize(value: TextExpansionList): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): TextExpansionList {
            return tryOrNull { Json.decodeFromString<TextExpansionList>(value) } ?: TextExpansionList()
        }
    }
}

/** Result of a successful rule match. [deleteLength] chars before the cursor must be removed first. */
data class TextExpansionMatch(
    val rule: TextExpansion,
    val deleteLength: Int,
    /** True if the replacement must be followed by the space the user typed. */
    val keepTrailingSpace: Boolean,
)

object TextExpander {
    const val DEFAULT_BROADCAST_ACTION = "dev.dannycipriani.florisboard.TEXT_EXPANDED"
    const val EXTRA_ABBREVIATION = "abbreviation"
    const val EXTRA_EXPANSION = "expansion"
    const val CLIPBOARD_PLACEHOLDER = "{clipboard}"

    /** How many characters before the cursor are inspected. Longer abbreviations are never matched. */
    const val MAX_LOOKBEHIND = 64

    /**
     * Called after the user typed a space. [textBefore] already ends with that space.
     */
    fun findOnSpace(rules: List<TextExpansion>, textBefore: String): TextExpansionMatch? {
        if (!textBefore.endsWith(' ')) return null
        val withoutSpace = textBefore.dropLast(1)
        val token = withoutSpace.takeLastWhile { !it.isWhitespace() }
        if (token.isEmpty()) return null
        val rule = rules.firstOrNull { r ->
            !r.instant && r.abbreviation.isNotEmpty() && r.abbreviation.equals(token, ignoreCase = !r.caseSensitive)
        } ?: return null
        return TextExpansionMatch(rule, deleteLength = token.length + 1, keepTrailingSpace = true)
    }

    /**
     * Called after any text was typed. Matches instant rules whose abbreviation ends right at the cursor and
     * starts at the beginning of a word.
     */
    fun findInstant(rules: List<TextExpansion>, textBefore: String): TextExpansionMatch? {
        val rule = rules
            .filter { r ->
                r.instant && r.abbreviation.isNotEmpty() &&
                    textBefore.endsWith(r.abbreviation, ignoreCase = !r.caseSensitive) &&
                    textBefore.length.let { len ->
                        val start = len - r.abbreviation.length
                        start == 0 || textBefore[start - 1].isWhitespace()
                    }
            }
            .maxByOrNull { it.abbreviation.length } ?: return null
        return TextExpansionMatch(rule, deleteLength = rule.abbreviation.length, keepTrailingSpace = false)
    }

    fun resolve(rule: TextExpansion, clipboardText: String?): String {
        return rule.expansion.replace(CLIPBOARD_PLACEHOLDER, clipboardText ?: "")
    }
}

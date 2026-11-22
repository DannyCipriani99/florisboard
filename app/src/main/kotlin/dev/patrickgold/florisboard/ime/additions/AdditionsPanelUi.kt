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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.expander.TextExpander
import dev.patrickgold.florisboard.ime.expander.TextExpansion
import dev.patrickgold.florisboard.ime.expander.TextExpansionList
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val PanelBg = Color(0xFF263238)
private val ChipBg = Color(0xFF37474F)
private val ChipOn = Color(0xFF00897B)
private val FieldBg = Color(0xFF1C262B)
private val Accent = Color(0xFF4DD0E1)

@Composable
fun AdditionsPanelHost() {
    when (AdditionsPanel.kind) {
        AdditionsPanelKind.NONE -> Unit
        AdditionsPanelKind.FIND_REPLACE -> FindReplacePanel()
        AdditionsPanelKind.EXPANDER -> ExpanderPanel()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ChipOn else ChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun VirtualField(
    modifier: Modifier,
    hint: String,
    value: String,
    focused: Boolean,
    onFocus: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FieldBg)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Accent else ChipBg), RoundedCornerShape(8.dp))
            .clickable(onClick = onFocus)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty()) {
            Text(hint, color = Color(0xFF78909C), fontSize = 15.sp, maxLines = 1)
        } else {
            Text(
                value + if (focused) "|" else "",
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FindReplacePanel() {
    val prefs by FlorisPreferenceStore
    val regexAllowed by prefs.additions.findReplaceRegex.observeAsState()
    val p = AdditionsPanel
    Column(
        modifier = Modifier.fillMaxWidth().background(PanelBg).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            VirtualField(
                Modifier.weight(1f), stringResource(R.string.panel__find_hint), p.find,
                p.focus == PanelField.FIND,
            ) { p.focus = PanelField.FIND }
            VirtualField(
                Modifier.weight(1f), stringResource(R.string.panel__replace_hint), p.replace,
                p.focus == PanelField.REPLACE,
            ) { p.focus = PanelField.REPLACE }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Aa", p.caseSensitive) { p.caseSensitive = !p.caseSensitive }
            if (regexAllowed) {
                Chip(".*", p.useRegex) { p.useRegex = !p.useRegex }
            }
            Chip("◀") { p.findNext(forward = false) }
            Chip("▶") { p.findNext() }
            Chip(stringResource(R.string.panel__replace)) { p.replaceCurrent() }
            Chip(stringResource(R.string.panel__replace_all)) { p.replaceAll() }
            Chip("✕") { p.close() }
            if (p.status.isNotEmpty()) {
                Text(p.status, color = Accent, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExpanderPanel() {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val clipboardManager by context.clipboardManager()
    val keyboardManager by context.keyboardManager()
    val scope = rememberCoroutineScope()
    val enabled by prefs.expander.enabled.observeAsState()
    val ruleList by prefs.expander.rules.observeAsState()
    val p = AdditionsPanel
    Column(
        modifier = Modifier.fillMaxWidth().background(PanelBg).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(
                stringResource(if (enabled) R.string.panel__expander_on else R.string.panel__expander_off),
                enabled,
            ) { scope.launch { prefs.expander.enabled.set(!enabled) } }
            Chip("⚙") { FlorisImeService.launchSettings("settings/expander") }
            Chip("✕") { p.close() }
            // tap a rule to insert its expansion directly
            for (rule in ruleList.rules) {
                Chip(rule.abbreviation) {
                    val clip = clipboardManager.primaryClip?.text
                    FlorisImeService.currentInputConnection()
                        ?.commitText(TextExpander.resolve(rule, clip), 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            VirtualField(
                Modifier.weight(1f), stringResource(R.string.panel__new_abbreviation), p.abbreviation, true,
            ) { }
            Chip(stringResource(R.string.panel__save_from_clipboard)) {
                val clip = clipboardManager.primaryClip?.text?.toString().orEmpty()
                if (p.abbreviation.isNotEmpty() && clip.isNotEmpty()) {
                    scope.launch {
                        val rules = ruleList.rules.filter { it.abbreviation != p.abbreviation }
                        prefs.expander.rules.set(
                            TextExpansionList(rules + TextExpansion(abbreviation = p.abbreviation, expansion = clip))
                        )
                        prefs.expander.enabled.set(true)
                        p.abbreviation = ""
                        p.status = "✓"
                    }
                }
            }
            if (p.status.isNotEmpty()) {
                Text(p.status, color = Accent, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

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

package dev.patrickgold.florisboard.app.settings.expander

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.components.FlorisScreenNg
import dev.patrickgold.florisboard.app.components.ScrollableScreenColumn
import dev.patrickgold.florisboard.app.components.SegmentedListColumn
import dev.patrickgold.florisboard.ime.expander.TextExpander
import dev.patrickgold.florisboard.ime.expander.TextExpansion
import dev.patrickgold.florisboard.ime.expander.TextExpansionList
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ExpanderScreen() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val ruleList by prefs.expander.rules.collectAsState()
    val rules = ruleList.rules

    // index of the rule being edited, -1 = adding a new rule, null = no dialog
    var editIndex by remember { mutableStateOf<Int?>(null) }

    FlorisScreenNg(
        title = stringRes(R.string.settings__expander__title),
        previewFieldVisible = true,
    ) { contentPadding ->
        ScrollableScreenColumn(contentPadding) {
            SegmentedListColumn {
                val count = 2
                SwitchPreference(
                    prefs.expander.enabled,
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    title = stringRes(R.string.pref__expander__enabled__label),
                    summary = stringRes(R.string.pref__expander__enabled__summary),
                )
                Preference(
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    title = stringRes(R.string.settings__expander__add),
                    summary = stringRes(R.string.settings__expander__intent_hint, "action" to TextExpander.DEFAULT_BROADCAST_ACTION),
                    onClick = { editIndex = -1 },
                )
            }

            if (rules.isNotEmpty()) {
                SegmentedListColumn(title = stringRes(R.string.settings__expander__rules_title)) {
                    rules.forEachIndexed { index, rule ->
                        Preference(
                            shapes = ListItemDefaults.segmentedShapes(index, rules.size),
                            title = rule.abbreviation,
                            summary = rule.expansion.ifEmpty { "-" },
                            onClick = { editIndex = index },
                        )
                    }
                }
            }
        }

        editIndex?.let { index ->
            RuleDialog(
                initial = rules.getOrNull(index),
                onDismiss = { editIndex = null },
                onDelete = if (index >= 0) {
                    {
                        scope.launch {
                            prefs.expander.rules.set(
                                TextExpansionList(rules.filterIndexed { i, _ -> i != index })
                            )
                            editIndex = null
                        }
                    }
                } else null,
                onConfirm = { newRule ->
                    scope.launch {
                        val newRules = if (index >= 0) {
                            rules.mapIndexed { i, old -> if (i == index) newRule else old }
                        } else {
                            rules + newRule
                        }
                        prefs.expander.rules.set(TextExpansionList(newRules))
                        editIndex = null
                    }
                },
            )
        }
    }
}

@Composable
private fun RuleDialog(
    initial: TextExpansion?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (TextExpansion) -> Unit,
) {
    var abbreviation by remember { mutableStateOf(initial?.abbreviation ?: "") }
    var expansion by remember { mutableStateOf(initial?.expansion ?: "") }
    var instant by remember { mutableStateOf(initial?.instant ?: false) }
    var caseSensitive by remember { mutableStateOf(initial?.caseSensitive ?: true) }
    var broadcast by remember { mutableStateOf(initial?.broadcastEnabled ?: false) }
    var broadcastAction by remember { mutableStateOf(initial?.broadcastAction ?: "") }

    val abbreviationValid = abbreviation.isNotEmpty() && abbreviation.none { it.isWhitespace() }
    val hasEffect = expansion.isNotEmpty() || broadcast

    JetPrefAlertDialog(
        title = stringRes(
            if (initial == null) R.string.settings__expander__add else R.string.settings__expander__edit
        ),
        confirmLabel = stringRes(R.string.action__save),
        confirmEnabled = abbreviationValid && hasEffect,
        onConfirm = {
            onConfirm(
                TextExpansion(
                    abbreviation = abbreviation,
                    expansion = expansion,
                    instant = instant,
                    caseSensitive = caseSensitive,
                    broadcastEnabled = broadcast,
                    broadcastAction = broadcastAction.trim(),
                )
            )
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = abbreviation,
                onValueChange = { abbreviation = it },
                singleLine = true,
                label = { Text(stringRes(R.string.settings__expander__abbreviation)) },
                isError = abbreviation.isNotEmpty() && !abbreviationValid,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = expansion,
                onValueChange = { expansion = it },
                label = { Text(stringRes(R.string.settings__expander__expansion)) },
                supportingText = { Text(stringRes(
                    R.string.settings__expander__clipboard_hint,
                    "placeholder" to TextExpander.CLIPBOARD_PLACEHOLDER,
                )) },
            )
            SwitchRow(stringRes(R.string.settings__expander__instant), instant) { instant = it }
            SwitchRow(stringRes(R.string.settings__expander__case_sensitive), caseSensitive) { caseSensitive = it }
            SwitchRow(stringRes(R.string.settings__expander__broadcast), broadcast) { broadcast = it }
            if (broadcast) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = broadcastAction,
                    onValueChange = { broadcastAction = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.settings__expander__broadcast_action)) },
                    placeholder = { Text(TextExpander.DEFAULT_BROADCAST_ACTION) },
                )
            }
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringRes(R.string.action__delete))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.weight(1f), text = label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bodhalauncher.engine.HomeAction
import com.bodhalauncher.engine.LibraryGroup
import com.bodhalauncher.engine.UNGROUPED_GROUP

/** Names a user group: create when [existing] is null, else rename or delete it. */
@Composable
fun GroupEditorDialog(
    existing: String?,
    /** Names already in use; Save stays disabled for them and the reserved section title. */
    taken: List<String>,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    var name by remember { mutableStateOf(existing.orEmpty()) }
    val trimmed = name.trim()
    val saveable = trimmed.isNotEmpty() &&
        trimmed != existing &&
        !trimmed.equals(UNGROUPED_GROUP, ignoreCase = true) &&
        trimmed !in taken
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(24.dp),
        ) {
            Text(
                text = if (existing == null) "New group" else "Group",
                color = colors.inkMuted,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(20.dp))
            Row {
                if (existing != null) {
                    Text(
                        text = "Delete",
                        color = colors.inkMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable { onDelete(); onDismiss() },
                    )
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Save",
                    color = colors.accent,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = saveable) {
                        onSave(trimmed)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** Toggles [app]'s membership in each user group; membership may span groups. */
@Composable
fun GroupPickerDialog(
    app: HomeAction,
    groups: List<LibraryGroup>,
    onToggle: (LibraryGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.ground)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = app.label,
                color = colors.inkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (groups.isEmpty()) {
                Text(
                    text = "No groups yet — create one in the Groups layout",
                    color = colors.inkMuted,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
            groups.forEach { group ->
                val member = app.id in group.appIds
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                    Text(
                        // Wording over glyphs: ADR 0010 keeps marks off the machinery rows.
                        text = if (member) "${group.name} · added" else group.name,
                        color = if (member) colors.ink else colors.inkMuted,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(group) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}

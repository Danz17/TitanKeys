package com.titankeys.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.titankeys.keyboard.inputmethod.ui.BarLayoutConfig
import com.titankeys.keyboard.inputmethod.ui.BarSlotAction
import com.titankeys.keyboard.inputmethod.ui.BarSlotConfig

/**
 * Settings screen for customizing the keyboard bar layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarLayoutSettingsScreen(
  modifier: Modifier = Modifier,
  onBack: () -> Unit
) {
  val context = LocalContext.current

  var barConfig by remember {
    mutableStateOf(SettingsManager.getBarLayoutConfig(context))
  }

  var selectedSlotIndex by remember { mutableStateOf<Int?>(null) }
  var showActionPicker by remember { mutableStateOf(false) }
  var isSelectingLongPress by remember { mutableStateOf(false) }

  // Handle system back button
  BackHandler {
    if (selectedSlotIndex != null) {
      selectedSlotIndex = null
    } else {
      onBack()
    }
  }

  Scaffold(
    topBar = {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.statusBars),
        tonalElevation = 1.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = {
            if (selectedSlotIndex != null) {
              selectedSlotIndex = null
            } else {
              onBack()
            }
          }) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
          Text(
            text = "Bar Layout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
      }
    }
  ) { paddingValues ->
    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
    ) {
      // Bar Preview
      Text(
        text = "Preview",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      BarPreview(
        config = barConfig,
        selectedSlotIndex = selectedSlotIndex,
        onSlotClick = { index ->
          selectedSlotIndex = index
          showActionPicker = true
          isSelectingLongPress = false
        }
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Slot Count Selector
      Text(
        text = "Number of Slots",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        (BarLayoutConfig.MIN_SLOT_COUNT..BarLayoutConfig.MAX_SLOT_COUNT).forEach { count ->
          FilterChip(
            selected = barConfig.slotCount == count,
            onClick = {
              barConfig = barConfig.withSlotCount(count)
              SettingsManager.setBarLayoutConfig(context, barConfig)
            },
            label = { Text("$count") }
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Slot Configuration
      Text(
        text = "Slot Configuration",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      Text(
        text = "Tap a slot in the preview to configure it, or use the list below.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp)
      )

      // Slot list
      for (i in 0 until barConfig.slotCount) {
        val slotConfig = barConfig.getSlot(i)
        SlotConfigItem(
          slotIndex = i,
          slotConfig = slotConfig,
          isFirst = i == 0,
          isLast = i == barConfig.slotCount - 1,
          onTapActionClick = {
            selectedSlotIndex = i
            isSelectingLongPress = false
            showActionPicker = true
          },
          onLongPressActionClick = {
            selectedSlotIndex = i
            isSelectingLongPress = true
            showActionPicker = true
          }
        )
        if (i < barConfig.slotCount - 1) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Reset button
      OutlinedButton(
        onClick = {
          SettingsManager.resetBarLayoutConfig(context)
          barConfig = SettingsManager.getBarLayoutConfig(context)
        },
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Reset to Default")
      }
    }
  }

  // Action picker dialog
  if (showActionPicker && selectedSlotIndex != null) {
    ActionPickerDialog(
      currentAction = if (isSelectingLongPress) {
        barConfig.getSlot(selectedSlotIndex!!).longPressAction
      } else {
        barConfig.getSlot(selectedSlotIndex!!).tapAction
      },
      isLongPress = isSelectingLongPress,
      onActionSelected = { action ->
        val slotIndex = selectedSlotIndex!!
        val currentSlot = barConfig.getSlot(slotIndex)
        val updatedSlot = if (isSelectingLongPress) {
          currentSlot.copy(longPressAction = action)
        } else {
          currentSlot.copy(tapAction = action)
        }
        barConfig = barConfig.withSlot(updatedSlot)
        SettingsManager.setBarLayoutConfig(context, barConfig)
        showActionPicker = false
        selectedSlotIndex = null
      },
      onDismiss = {
        showActionPicker = false
        selectedSlotIndex = null
      }
    )
  }
}

@Composable
private fun BarPreview(
  config: BarLayoutConfig,
  selectedSlotIndex: Int?,
  onSlotClick: (Int) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF111111))
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    for (i in 0 until config.slotCount) {
      val slotConfig = config.getSlot(i)
      val isSelected = selectedSlotIndex == i

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(4.dp))
          .background(
            if (isSelected) Color(0xFF3366FF)
            else Color(0xFF222222)
          )
          .border(
            width = if (isSelected) 2.dp else 0.dp,
            color = if (isSelected) Color.White else Color.Transparent,
            shape = RoundedCornerShape(4.dp)
          )
          .clickable { onSlotClick(i) },
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = getActionLabel(slotConfig.tapAction),
          color = Color.White,
          fontSize = 10.sp,
          textAlign = TextAlign.Center,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun SlotConfigItem(
  slotIndex: Int,
  slotConfig: BarSlotConfig,
  isFirst: Boolean,
  isLast: Boolean,
  onTapActionClick: () -> Unit,
  onLongPressActionClick: () -> Unit
) {
  val slotLabel = when {
    isFirst -> "Slot ${slotIndex + 1} (Left Edge)"
    isLast -> "Slot ${slotIndex + 1} (Right Edge)"
    else -> "Slot ${slotIndex + 1}"
  }

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Text(
      text = slotLabel,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Tap action
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Tap",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedCard(
          onClick = onTapActionClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = getActionLabel(slotConfig.tapAction),
              style = MaterialTheme.typography.bodyMedium
            )
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit",
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Long-press action
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Long Press",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedCard(
          onClick = onLongPressActionClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = getActionLabel(slotConfig.longPressAction),
              style = MaterialTheme.typography.bodyMedium
            )
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit",
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActionPickerDialog(
  currentAction: BarSlotAction,
  isLongPress: Boolean,
  onActionSelected: (BarSlotAction) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (isLongPress) "Long Press Action" else "Tap Action")
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        BarSlotAction.entries.forEach { action ->
          val isSelected = action == currentAction
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { onActionSelected(action) },
            color = if (isSelected) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surface
            }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = getActionIcon(action),
                contentDescription = null,
                tint = if (isSelected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurface
                }
              )
              Column {
                Text(
                  text = getActionLabel(action),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  }
                )
                Text(
                  text = getActionDescription(action),
                  style = MaterialTheme.typography.bodySmall,
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  }
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

private fun getActionLabel(action: BarSlotAction): String {
  return when (action) {
    BarSlotAction.NONE -> "None"
    BarSlotAction.VOICE -> "Voice"
    BarSlotAction.CLIPBOARD -> "Clipboard"
    BarSlotAction.CLIPBOARD_HISTORY -> "Clip History"
    BarSlotAction.EMOJI -> "Emoji"
    BarSlotAction.SETTINGS -> "Settings"
    BarSlotAction.LANGUAGE_SWITCH -> "Language"
    BarSlotAction.APP_LAUNCH -> "App Launch"
  }
}

private fun getActionDescription(action: BarSlotAction): String {
  return when (action) {
    BarSlotAction.NONE -> "Empty slot (shows suggestions)"
    BarSlotAction.VOICE -> "Start speech recognition"
    BarSlotAction.CLIPBOARD -> "Quick paste latest item"
    BarSlotAction.CLIPBOARD_HISTORY -> "Open clipboard history"
    BarSlotAction.EMOJI -> "Open emoji picker"
    BarSlotAction.SETTINGS -> "Open keyboard settings"
    BarSlotAction.LANGUAGE_SWITCH -> "Switch keyboard language"
    BarSlotAction.APP_LAUNCH -> "Launch an app (coming soon)"
  }
}

private fun getActionIcon(action: BarSlotAction): androidx.compose.ui.graphics.vector.ImageVector {
  return when (action) {
    BarSlotAction.NONE -> Icons.Default.CheckBoxOutlineBlank
    BarSlotAction.VOICE -> Icons.Default.Mic
    BarSlotAction.CLIPBOARD -> Icons.Default.ContentPaste
    BarSlotAction.CLIPBOARD_HISTORY -> Icons.Default.History
    BarSlotAction.EMOJI -> Icons.Default.EmojiEmotions
    BarSlotAction.SETTINGS -> Icons.Default.Settings
    BarSlotAction.LANGUAGE_SWITCH -> Icons.Default.Language
    BarSlotAction.APP_LAUNCH -> Icons.Default.Apps
  }
}

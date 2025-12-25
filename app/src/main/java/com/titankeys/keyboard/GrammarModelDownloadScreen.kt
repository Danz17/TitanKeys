package com.titankeys.keyboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.titankeys.keyboard.core.suggestions.GrammarModelDownloader
import kotlinx.coroutines.launch

/**
 * Available grammar model languages with display names and sizes
 */
data class GrammarLanguageInfo(
  val code: String,
  val displayName: String,
  val nativeName: String,
  val estimatedSizeMB: Int,
  val available: Boolean = true
)

private val AVAILABLE_LANGUAGES = listOf(
  GrammarLanguageInfo("en", "English", "English", 35, true),
  GrammarLanguageInfo("de", "German", "Deutsch", 35, false),
  GrammarLanguageInfo("es", "Spanish", "Español", 35, false),
  GrammarLanguageInfo("fr", "French", "Français", 35, false),
  GrammarLanguageInfo("it", "Italian", "Italiano", 35, false),
  GrammarLanguageInfo("pt", "Portuguese", "Português", 35, false),
  GrammarLanguageInfo("pl", "Polish", "Polski", 35, false),
  GrammarLanguageInfo("ru", "Russian", "Русский", 35, false),
  GrammarLanguageInfo("lt", "Lithuanian", "Lietuvių", 35, false)
)

/**
 * Screen for downloading and managing grammar AI models
 */
@Composable
fun GrammarModelDownloadScreen(
  modifier: Modifier = Modifier,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  // Track download progress for each language
  val downloadProgress = remember { mutableStateMapOf<String, Float>() }
  val downloadingLanguages = remember { mutableStateMapOf<String, Boolean>() }

  // Track downloaded models - refreshes on recomposition
  var downloadedModels by remember {
    mutableStateOf(SettingsManager.getDownloadedGrammarModels(context))
  }

  // Dialog state
  var showDeleteDialog by remember { mutableStateOf<String?>(null) }

  // Model downloader
  val downloader = remember { GrammarModelDownloader(context) }

  // Cleanup on dispose
  DisposableEffect(Unit) {
    onDispose {
      downloader.cancelAllDownloads()
    }
  }

  BackHandler { onBack() }

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
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
          Text(
            text = "Grammar AI Models",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->
    Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
    ) {
      // Header info
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Download Language Models",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = "Grammar AI uses neural network models to detect and correct errors. " +
              "Download models for the languages you need. Each model is approximately 60MB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
          )
          Text(
            text = "Without downloaded models, rule-based correction will be used.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
          )
        }
      }

      // Language list
      AVAILABLE_LANGUAGES.forEach { language ->
        val isDownloaded = downloadedModels.contains(language.code)
        val isDownloading = downloadingLanguages[language.code] == true
        val progress = downloadProgress[language.code] ?: 0f

        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = language.available && !isDownloading) {
              if (isDownloaded) {
                // Show delete confirmation
                showDeleteDialog = language.code
              } else {
                // Start download
                downloadingLanguages[language.code] = true
                downloadProgress[language.code] = 0f

                scope.launch {
                  val success = downloader.downloadModel(
                    languageCode = language.code,
                    onProgress = { prog ->
                      downloadProgress[language.code] = prog
                    }
                  )

                  downloadingLanguages[language.code] = false

                  if (success) {
                    SettingsManager.addDownloadedGrammarModel(context, language.code)
                    downloadedModels = SettingsManager.getDownloadedGrammarModels(context)
                    snackbarHostState.showSnackbar(
                      "${language.displayName} model downloaded successfully"
                    )
                  } else {
                    snackbarHostState.showSnackbar(
                      "Failed to download ${language.displayName} model"
                    )
                  }
                }
              }
            }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Language,
              contentDescription = null,
              tint = if (language.available) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
              modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = language.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Medium,
                  color = if (language.available) {
                    MaterialTheme.colorScheme.onSurface
                  } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                  }
                )
                if (language.displayName != language.nativeName) {
                  Text(
                    text = "(${language.nativeName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                      alpha = if (language.available) 1f else 0.38f
                    )
                  )
                }
              }

              if (isDownloading) {
                LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                )
              } else {
                Text(
                  text = when {
                    !language.available -> "Coming soon"
                    isDownloaded -> "Downloaded"
                    else -> "~${language.estimatedSizeMB}MB"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = when {
                    !language.available -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isDownloaded -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                  }
                )
              }
            }

            // Status icon
            when {
              isDownloading -> {
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  strokeWidth = 2.dp
                )
              }
              isDownloaded -> {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Downloaded",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(24.dp)
                )
              }
              language.available -> {
                Icon(
                  imageVector = Icons.Filled.Download,
                  contentDescription = "Download",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(24.dp)
                )
              }
            }
          }
        }
      }

      // Total size info
      val downloadedCount = downloadedModels.size
      if (downloadedCount > 0) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = MaterialTheme.shapes.small
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Total storage used:",
              style = MaterialTheme.typography.bodyMedium
            )
            Text(
              text = "~${downloadedCount * 60}MB",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }
  }

  // Delete confirmation dialog
  showDeleteDialog?.let { languageCode ->
    val language = AVAILABLE_LANGUAGES.find { it.code == languageCode }
    AlertDialog(
      onDismissRequest = { showDeleteDialog = null },
      icon = {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
      },
      title = {
        Text("Delete ${language?.displayName} Model?")
      },
      text = {
        Text("This will remove the downloaded grammar model. You can download it again later.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            scope.launch {
              downloader.deleteModel(languageCode)
              SettingsManager.removeDownloadedGrammarModel(context, languageCode)
              downloadedModels = SettingsManager.getDownloadedGrammarModels(context)
              snackbarHostState.showSnackbar("${language?.displayName} model deleted")
            }
            showDeleteDialog = null
          }
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

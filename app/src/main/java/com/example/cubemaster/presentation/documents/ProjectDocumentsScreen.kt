package com.example.cubemaster.presentation.documents

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cubemaster.ui.components.AttachmentsSection
import com.example.cubemaster.ui.components.CubeMasterTopBar
import com.example.cubemaster.ui.components.EmptyState
import com.example.cubemaster.ui.components.GlassCard
import com.example.cubemaster.ui.components.InfoCard
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun ProjectDocumentsScreen(
    onBack: () -> Unit,
    viewModel: ProjectDocumentsViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle(initialValue = emptyList())
    val hazeState = rememberHazeState()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CubeMasterTopBar(title = "Документи проєкту", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard("Договір, техпаспорт, технічні умови та інші документи проєкту — зберігаються разом із проєктом.")
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (documents.isEmpty()) {
                        EmptyState("Немає доданих документів.")
                    }
                    AttachmentsSection(
                        attachments = documents,
                        onAddPhoto = { uri -> viewModel.addPhoto(uri) },
                        onAddPdf = { uri -> viewModel.addPdf(uri) },
                        onAddNote = { text -> viewModel.addNote(text) },
                        onDelete = { viewModel.deleteAttachment(it) },
                        initiallyExpanded = true
                    )
                }
            }
        }
    }
}

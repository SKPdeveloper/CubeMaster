package com.example.cubemaster.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.model.Project
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.data.sync.SyncWorker
import com.example.cubemaster.domain.repository.ProjectRepository
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val auth: AuthRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState(isLoading = true))
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    private val uid: String get() = auth.currentUserId ?: ""

    init {
        viewModelScope.launch {
            repo.observeProjects(uid).collect { projects ->
                _state.update { it.copy(projects = projects, isLoading = false) }
            }
        }
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            SyncWorker.buildOneTimeRequest()
        )
    }

    fun createProject(title: String, address: String?, documentedAreaM2: Double?) {
        viewModelScope.launch {
            try {
                repo.createProject(uid, title, address, documentedAreaM2)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateProject(project: Project, title: String, address: String?, documentedAreaM2: Double?) {
        viewModelScope.launch {
            try {
                repo.updateProject(project.copy(title = title, address = address, documentedAreaM2 = documentedAreaM2))
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            try {
                repo.deleteProject(project.id)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

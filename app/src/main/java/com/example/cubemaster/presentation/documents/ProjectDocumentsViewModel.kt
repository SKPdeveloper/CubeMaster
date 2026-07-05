package com.example.cubemaster.presentation.documents

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.model.Attachment
import com.cubemaster.core.model.AttachmentParent
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.domain.repository.AttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDocumentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val attachmentRepo: AttachmentRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"]!!
    private val uid: String get() = auth.currentUserId ?: ""

    val documents = attachmentRepo.observeProjectDocuments(projectId)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() = _error.update { null }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, null, AttachmentParent.Project, projectId, uri)
            } catch (e: Exception) {
                _error.update { "Не вдалось завантажити фото: ${e.message}" }
            }
        }
    }

    fun addPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                attachmentRepo.addPdf(uid, projectId, null, AttachmentParent.Project, projectId, uri)
            } catch (e: Exception) {
                _error.update { "Не вдалось завантажити PDF: ${e.message}" }
            }
        }
    }

    fun addNote(text: String) {
        viewModelScope.launch { attachmentRepo.addNote(projectId, null, AttachmentParent.Project, projectId, text) }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch { attachmentRepo.delete(uid, attachment) }
    }
}

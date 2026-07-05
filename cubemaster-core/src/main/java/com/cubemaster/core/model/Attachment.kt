package com.cubemaster.core.model

enum class AttachmentKind { Photo, Pdf, Note }

// Demolition прив'язується на рівні кімнати (parentId = roomId) — окремих завдань демонтажу в UI ще немає.
enum class AttachmentParent { Project, Room, Surface, Demolition }

data class Attachment(
    val id: String,
    val projectId: String,
    val roomId: String?,
    val parentType: AttachmentParent,
    val parentId: String,
    val kind: AttachmentKind,
    val fileUrl: String?,
    val textContent: String?,
    val mimeType: String?,
    val createdAt: Long,
    val syncState: SyncState = SyncState.PendingUpload
)

package com.cubemaster.core.model

import java.time.Instant

data class Project(
    val id: String,
    val ownerId: String,
    val title: String,
    val address: String?,
    val documentedAreaM2: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState = SyncState.PendingUpload
)

enum class SyncState { Synced, PendingUpload, Conflict }

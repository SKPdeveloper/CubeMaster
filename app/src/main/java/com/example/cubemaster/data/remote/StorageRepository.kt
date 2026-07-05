package com.example.cubemaster.data.remote

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val storage: FirebaseStorage
) {
    suspend fun uploadAttachment(
        uid: String,
        projectId: String,
        parentType: String,
        parentId: String,
        uri: Uri,
        extension: String
    ): String {
        val ref = storage.reference
            .child("users/$uid/projects/$projectId/attachments/$parentType/$parentId/${UUID.randomUUID()}.$extension")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadCompanyLogo(uid: String, uri: Uri): String {
        val ref = storage.reference.child("users/$uid/logo.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun deleteFile(downloadUrl: String) {
        storage.getReferenceFromUrl(downloadUrl).delete().await()
    }
}

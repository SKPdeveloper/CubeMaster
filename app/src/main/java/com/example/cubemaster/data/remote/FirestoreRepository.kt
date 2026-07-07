package com.example.cubemaster.data.remote

import com.cubemaster.core.model.*
import com.example.cubemaster.data.local.converter.EntityMapper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val json: Json
) {
    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun projectsCol(uid: String) = userDoc(uid).collection("projects")
    private fun roomsCol(uid: String, projectId: String) =
        projectsCol(uid).document(projectId).collection("rooms")
    private fun openingsCol(uid: String, projectId: String, roomId: String) =
        roomsCol(uid, projectId).document(roomId).collection("openings")
    private fun surfacesCol(uid: String, projectId: String, roomId: String) =
        roomsCol(uid, projectId).document(roomId).collection("surfaces")
    private fun demolitionCol(uid: String, projectId: String, roomId: String) =
        roomsCol(uid, projectId).document(roomId).collection("demolitionTasks")
    private fun estimatesCol(uid: String, projectId: String) =
        projectsCol(uid).document(projectId).collection("estimates")
    private fun attachmentsCol(uid: String, projectId: String) =
        projectsCol(uid).document(projectId).collection("attachments")

    // ---- Projects ----

    suspend fun uploadProject(uid: String, project: Project) {
        val data = mapOf(
            "id" to project.id,
            "ownerId" to project.ownerId,
            "title" to project.title,
            "address" to project.address,
            "createdAt" to project.createdAt.toEpochMilli(),
            "updatedAt" to project.updatedAt.toEpochMilli()
        )
        projectsCol(uid).document(project.id).set(data).await()
    }

    suspend fun fetchProjects(uid: String): List<Map<String, Any>> {
        val snapshot = projectsCol(uid).get().await()
        return snapshot.documents.mapNotNull { it.data }
    }

    suspend fun deleteProject(uid: String, projectId: String) {
        projectsCol(uid).document(projectId).delete().await()
    }

    // ---- Rooms ----

    suspend fun uploadRoom(uid: String, projectId: String, roomEntity: com.example.cubemaster.data.local.entity.RoomEntity) {
        val data = mapOf(
            "id" to roomEntity.id,
            "projectId" to roomEntity.projectId,
            "name" to roomEntity.name,
            "geometryType" to roomEntity.geometryType,
            "widthMm" to roomEntity.widthMm,
            "lengthMm" to roomEntity.lengthMm,
            "edgesJson" to roomEntity.edgesJson,
            "heightMode" to roomEntity.heightMode,
            "heightMm" to roomEntity.heightMm,
            "cornerHeightsMmJson" to roomEntity.cornerHeightsMmJson,
            "roomType" to roomEntity.roomType,
            "sortOrder" to roomEntity.sortOrder,
            "originXM" to roomEntity.originXM,
            "originYM" to roomEntity.originYM,
            "rotationDeg" to roomEntity.rotationDeg,
            "updatedAt" to System.currentTimeMillis()
        )
        roomsCol(uid, projectId).document(roomEntity.id).set(data).await()
    }

    suspend fun fetchRooms(uid: String, projectId: String): List<Map<String, Any>> {
        return roomsCol(uid, projectId).get().await().documents.mapNotNull { it.data }
    }

    // ---- Surfaces ----

    suspend fun uploadSurface(
        uid: String, projectId: String, roomId: String,
        surface: com.example.cubemaster.data.local.entity.SurfaceEntity
    ) {
        val data = mapOf(
            "id" to surface.id,
            "roomId" to surface.roomId,
            "kind" to surface.kind,
            "wallEdgeIndex" to surface.wallEdgeIndex,
            "layersJson" to surface.layersJson,
            "updatedAt" to System.currentTimeMillis()
        )
        surfacesCol(uid, projectId, roomId).document(surface.id).set(data).await()
    }

    suspend fun fetchSurfaces(uid: String, projectId: String, roomId: String): List<Map<String, Any>> =
        surfacesCol(uid, projectId, roomId).get().await().documents.mapNotNull { it.data }

    // ---- Material catalog (shared, read-only for client) ----

    suspend fun fetchMaterialCatalog(): List<Map<String, Any>> =
        firestore.collection("materialCatalog").get().await().documents.mapNotNull { it.data }

    // ---- Prices ----

    suspend fun fetchPublicPrices(sku: String): List<Map<String, Any>> =
        firestore.collection("priceEntries")
            .whereEqualTo("materialSku", sku)
            .orderBy("fetchedAt")
            .get().await().documents.mapNotNull { it.data }

    suspend fun uploadManualPrice(uid: String, price: PriceEntry) {
        val data = mapOf(
            "id" to price.id,
            "materialSku" to price.materialSku,
            "vendor" to price.vendor,
            "unitPrice" to price.unitPrice,
            "currency" to price.currency,
            "source" to price.source.name,
            "fetchedAt" to price.fetchedAt.toEpochMilli(),
            "vendorUrl" to price.vendorUrl
        )
        userDoc(uid).collection("manualPrices").document(price.id).set(data).await()
    }

    // ---- Estimates ----

    suspend fun uploadEstimate(uid: String, estimate: com.example.cubemaster.data.local.entity.EstimateEntity) {
        val data = mapOf(
            "id" to estimate.id,
            "projectId" to estimate.projectId,
            "markupPercent" to estimate.markupPercent,
            "linesJson" to estimate.linesJson,
            "createdAt" to estimate.createdAt,
            "updatedAt" to System.currentTimeMillis()
        )
        estimatesCol(uid, estimate.projectId).document(estimate.id).set(data).await()
    }

    // ---- Attachments ----

    suspend fun uploadAttachment(uid: String, attachment: com.example.cubemaster.data.local.entity.AttachmentEntity) {
        val data = mapOf(
            "id" to attachment.id,
            "projectId" to attachment.projectId,
            "roomId" to attachment.roomId,
            "parentType" to attachment.parentType,
            "parentId" to attachment.parentId,
            "kind" to attachment.kind,
            "fileUrl" to attachment.fileUrl,
            "textContent" to attachment.textContent,
            "mimeType" to attachment.mimeType,
            "createdAt" to attachment.createdAt
        )
        attachmentsCol(uid, attachment.projectId).document(attachment.id).set(data).await()
    }

    suspend fun deleteAttachment(uid: String, projectId: String, attachmentId: String) {
        attachmentsCol(uid, projectId).document(attachmentId).delete().await()
    }

    // ---- Cloud Functions ----

    suspend fun callGeneratePdf(uid: String, projectId: String, estimateId: String): String {
        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
        val data = hashMapOf("projectId" to projectId, "estimateId" to estimateId)
        val result = functions.getHttpsCallable("generateEstimatePdf").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as Map<String, Any>)["downloadUrl"] as String
    }

    suspend fun callGenerateXlsx(uid: String, projectId: String, estimateId: String): String {
        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
        val data = hashMapOf("projectId" to projectId, "estimateId" to estimateId)
        val result = functions.getHttpsCallable("generateEstimateXlsx").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as Map<String, Any>)["downloadUrl"] as String
    }

    suspend fun callRefreshPrices(skus: List<String>): Pair<Int, Int> {
        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
        val data = hashMapOf("skus" to skus)
        val result = functions.getHttpsCallable("refreshExternalPrices").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val map = result.data as Map<String, Any>
        return (map["updatedCount"] as Long).toInt() to (map["skippedCount"] as Long).toInt()
    }
}

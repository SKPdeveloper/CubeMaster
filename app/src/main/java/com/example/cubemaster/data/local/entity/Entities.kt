package com.example.cubemaster.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cubemaster.core.model.SyncState

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val address: String?,
    val documentedAreaM2: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String = SyncState.PendingUpload.name
)

@Entity(
    tableName = "rooms",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val geometryType: String, // "rectangle" | "polygon"
    val widthMm: Int?,
    val lengthMm: Int?,
    val edgesJson: String?,
    val heightMode: String,
    val heightMm: Int?,
    val cornerHeightsMmJson: String?,
    val roomType: String,
    val sortOrder: Int,
    val originXM: Double? = null,
    val originYM: Double? = null,
    val rotationDeg: Double = 0.0,
    val syncState: String = SyncState.PendingUpload.name
)

@Entity(
    tableName = "openings",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("roomId")]
)
data class OpeningEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val wallEdgeIndex: Int,
    val kind: String,
    val widthMm: Int,
    val heightMm: Int,
    val sillHeightMm: Int = 0,
    val offsetMm: Int = 0
)

@Entity(
    tableName = "surfaces",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("roomId")]
)
data class SurfaceEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val kind: String,
    val wallEdgeIndex: Int?,
    val layersJson: String
)

@Entity(
    tableName = "demolition_tasks",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("roomId")]
)
data class DemolitionTaskEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val kind: String,
    val paramsJson: String,
    val cachedResultJson: String?
)

@Entity(
    tableName = "estimates",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class EstimateEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val markupPercent: Double,
    val linesJson: String,
    val createdAt: Long,
    val syncState: String = SyncState.PendingUpload.name
)

// Поліморфний батько (Project | Room | Surface | Demolition) через parentType+parentId —
// на відміну від інших дочірніх сутностей тут немає єдиної батьківської таблиці, тому без @ForeignKey;
// каскадне видалення виконується вручну в репозиторіях (RoomRepository/SurfaceRepository/ProjectRepository).
@Entity(
    tableName = "attachments",
    indices = [Index("projectId"), Index("roomId"), Index(value = ["parentType", "parentId"])]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val roomId: String?,
    val parentType: String,
    val parentId: String,
    val kind: String,
    val fileUrl: String?,
    val textContent: String?,
    val mimeType: String?,
    val createdAt: Long,
    val syncState: String = SyncState.PendingUpload.name
)

@Entity(tableName = "material_catalog")
data class MaterialCatalogEntity(
    @PrimaryKey val sku: String,
    val nameUa: String,
    val category: String,
    val unit: String,
    val packSize: Double?,
    val densityKgM3: Double?,
    val consumptionNormJson: String
)

@Entity(tableName = "price_entries")
data class PriceEntryEntity(
    @PrimaryKey val id: String,
    val materialSku: String,
    val vendor: String,
    val unitPrice: Double,
    val currency: String = "UAH",
    val source: String,
    val fetchedAt: Long,
    val vendorUrl: String?
)

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val address: String?,
    val phone: String?,
    val email: String?,
    val logoUrl: String?
)

package com.cubemaster.core.model

data class Room(
    val id: String,
    val projectId: String,
    val name: String,
    val geometry: RoomGeometry,
    val heightMode: HeightMode,
    val heightMm: Int?,
    val cornerHeightsMm: List<Int>?,
    val roomType: RoomType,
    val sortOrder: Int,
    val syncState: SyncState = SyncState.PendingUpload
)

sealed class RoomGeometry {
    data class Rectangle(val widthMm: Int, val lengthMm: Int) : RoomGeometry()
    data class Polygon(val edges: List<Edge>) : RoomGeometry()
}

data class Edge(val lengthMm: Int, val interiorAngleDeg: Double)

enum class HeightMode { Uniform, PerCorner }

enum class RoomType { Living, Bathroom, Kitchen, KitchenWetZone, Balcony, Technical }

data class Opening(
    val id: String,
    val roomId: String,
    val wallEdgeIndex: Int,
    val kind: OpeningKind,
    val widthMm: Int,
    val heightMm: Int,
    val sillHeightMm: Int = 0
)

enum class OpeningKind { Window, Door, Passage }

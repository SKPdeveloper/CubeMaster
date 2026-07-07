package com.example.cubemaster.presentation.objectplan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubemaster.core.geometry.roomGeometryVertices
import com.cubemaster.core.geometry.transformVertices
import com.example.cubemaster.ui.components.CubeMasterTopBar
import com.example.cubemaster.ui.components.EmptyState
import com.example.cubemaster.ui.components.LoadingOverlay
import com.example.cubemaster.ui.components.PlacedRoom
import com.example.cubemaster.ui.components.PlanInteractionMode
import com.example.cubemaster.ui.components.RoomPlanCanvas

@Composable
fun ObjectPlanScreen(
    onRoomClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ObjectPlanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CubeMasterTopBar(title = "План об'єкта", onBack = onBack) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingOverlay()
                state.roomPlacements.isEmpty() -> EmptyState("Немає кімнат для розміщення. Додайте кімнати в проєкті.")
                else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        "Перетягніть кімнати, щоб розташувати їх відносно одна одної. Тапніть по кімнаті, щоб відкрити її геометрію.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    val placedRooms = state.roomPlacements.map { placement ->
                        val localVertices = roomGeometryVertices(placement.room.geometry)
                        val globalVertices = transformVertices(
                            localVertices, placement.originXM, placement.originYM, placement.room.rotationDeg
                        )
                        PlacedRoom(
                            roomId = placement.room.id,
                            label = placement.room.name,
                            vertices = globalVertices
                        )
                    }
                    RoomPlanCanvas(
                        rooms = placedRooms,
                        mode = PlanInteractionMode.ObjectOverview,
                        onRoomTap = onRoomClick,
                        onRoomDrag = { roomId, dx, dy -> viewModel.moveRoomLive(roomId, dx, dy) },
                        onRoomDragEnd = { roomId -> viewModel.persistRoomPlacement(roomId) },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

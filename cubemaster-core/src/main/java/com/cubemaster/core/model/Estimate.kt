package com.cubemaster.core.model

import java.time.Instant

data class Estimate(
    val id: String,
    val projectId: String,
    val markupPercent: Double,
    val lines: List<EstimateLine>,
    val createdAt: Instant = Instant.now(),
    val syncState: SyncState = SyncState.PendingUpload
)

data class EstimateLine(
    val id: String,
    val lineType: LineType,
    val refSku: String?,
    val description: String,
    val qty: Double,
    val unit: MeasurementUnit,
    val unitPrice: Double,
    val priceSource: PriceSource,
    val applyMarkup: Boolean,
    val roomId: String?
) {
    val total: Double get() = qty * unitPrice
}

enum class LineType { Material, Labor }

fun Estimate.grandTotal(): Double =
    lines.sumOf { line ->
        if (line.applyMarkup && markupPercent > 0)
            line.total * (1 + markupPercent / 100)
        else
            line.total
    }

fun Estimate.totalWithMarkup(line: EstimateLine): Double =
    if (line.applyMarkup && markupPercent > 0)
        line.total * (1 + markupPercent / 100)
    else
        line.total

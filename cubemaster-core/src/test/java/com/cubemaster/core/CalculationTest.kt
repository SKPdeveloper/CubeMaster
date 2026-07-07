package com.cubemaster.core

import com.cubemaster.core.calculation.*
import com.cubemaster.core.catalog.MaterialDefaults
import com.cubemaster.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CalculationTest {

    @Test
    fun `стяжка 10 кв м, 50 мм — правильна кількість мішків`() {
        val norm = MaterialDefaults.findBySku("SCREED_CP5_BAGS")!!.consumptionNorm
        val result = calculateScreed(10.0, 50.0, norm)
        // 10 * 50 * 0.323 = 161.5 кг → 7 мішків по 25кг
        assertEquals(7, result.bagsCount)
    }

    @Test
    fun `стяжка ЦПС 10 кв м, 50 мм — цемент і пісок`() {
        val result = calculateScreedCps(10.0, 50.0)
        // Обʼєм = 10 * 50/1000 = 0.5 м³
        // Цемент: 0.5 * (1/4) * 1300 = 162.5 кг → 4 мішки 50кг
        val volumeExpected = 0.5
        assertEquals(volumeExpected, result.volumeM3, 0.001)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `стяжка менша за мінімум — попередження`() {
        val norm = MaterialDefaults.findBySku("SCREED_CP5_BAGS")!!.consumptionNorm
        val result = calculateScreed(10.0, 10.0, norm)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `штукатурка 20 кв м, 15 мм — розрахунок мішків`() {
        val norm = MaterialDefaults.findBySku("PLASTER_GYPSUM_MANUAL")!!.consumptionNorm
        val result = calculatePlaster(20.0, 15.0, norm)
        // 20 * 15 * 0.875 = 262.5 кг → 11 мішків по 25кг
        assertEquals(11, result.bagsCount)
    }

    @Test
    fun `штукатурка приміщення з 5+ кутами — +3%`() {
        val norm = MaterialDefaults.findBySku("PLASTER_GYPSUM_MANUAL")!!.consumptionNorm
        val resultNormal = calculatePlaster(20.0, 15.0, norm, concaveCorners = 0)
        val resultComplex = calculatePlaster(20.0, 15.0, norm, concaveCorners = 5)
        assertEquals(resultNormal.mixMassKg * 1.03, resultComplex.mixMassKg, 0.01)
        assertTrue(resultComplex.warnings.isNotEmpty())
    }

    @Test
    fun `демонтаж залізобетону — обов'язкове попередження`() {
        val result = calculateWallRemoval(3.0, 2.7, 200.0, WallMaterial.ReinforcedConcrete, true)
        // Обсяг = 3 * 2.7 * 0.2 = 1.62 м³
        assertEquals(1.62 * 1.4, result.debrisVolumeM3, 0.01)
        assertTrue(result.debrisMassKg > 0)
        assertTrue(result.laborHours > 0)
    }

    @Test
    fun `розрахунок плитки з підрізкою 10%`() {
        val result = calculateFlooring(15.0, LayerType.FlooringTile, isDiagonal = false, isComplexGeometry = false)
        // 15 * 1.10 = 16.5 м² плитки
        val tileM2 = result.additionalLines.find { it.descriptionUa.contains("Плитка") }?.qty ?: 0.0
        assertEquals(16.5, tileM2, 0.01)
    }

    @Test
    fun `розрахунок плитки по діагоналі — 15%`() {
        val result = calculateFlooring(15.0, LayerType.FlooringTile, isDiagonal = true)
        val tileM2 = result.additionalLines.find { it.descriptionUa.contains("Плитка") }?.qty ?: 0.0
        assertEquals(15.0 * 1.15, tileM2, 0.01)
    }

    @Test
    fun `розбивка бюджету — підсумок з націнкою`() {
        val estimate = Estimate(
            id = "e1", projectId = "p1", markupPercent = 15.0,
            lines = listOf(
                EstimateLine("l1", LineType.Material, null, "Цемент", 10.0, MeasurementUnit.Bag50, 100.0, PriceSource.Manual, true, null),
                EstimateLine("l2", LineType.Labor, null, "Стяжка", 50.0, MeasurementUnit.M2, 80.0, PriceSource.Manual, false, null)
            )
        )
        val total = estimate.grandTotal()
        // l1: 10 * 100 * 1.15 = 1150; l2: 50 * 80 = 4000
        assertEquals(5150.0, total, 0.01)
    }

    @Test
    fun `сміття контейнери — розрахунок`() {
        assertEquals(2, debrisContainersCount(12.0, 8.0))
        assertEquals(1, debrisContainersCount(7.0, 8.0))
    }

    @Test
    fun `ґрунтовка 20 кв м — кількість літрів`() {
        val result = calculatePrimer(20.0, isPorous = false)
        val liters = result.additionalLines.firstOrNull()?.qty ?: 0.0
        assertEquals(20 * 0.125, liters, 0.01)
    }

    @Test
    fun `ґрунтовка пориста основа — коефіцієнт x1_5`() {
        val normal = calculatePrimer(20.0, isPorous = false)
        val porous = calculatePrimer(20.0, isPorous = true)
        val normalL = normal.additionalLines.firstOrNull()?.qty ?: 0.0
        val porousL = porous.additionalLines.firstOrNull()?.qty ?: 0.0
        assertEquals(normalL * 1.5, porousL, 0.01)
    }

    @Test
    fun `прорізання отвору в цегляній стіні — без попередження`() {
        val result = calculateOpeningCut(1.0, 2.0, WallMaterial.Brick)
        // periметр різу = 2*(1+2) = 6 м; продуктивність штроборізом 0.45 м/год
        assertEquals(6.0 / 0.45, result.laborHours, 0.01)
        assertEquals(0.4, result.debrisVolumeM3, 0.001)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `прорізання отвору в залізобетоні — попередження ДБН`() {
        val result = calculateOpeningCut(1.0, 2.0, WallMaterial.ReinforcedConcrete)
        // алмазне різання 0.75 м/год
        assertEquals(6.0 / 0.75, result.laborHours, 0.01)
        assertTrue(result.warnings.any { it.contains("ДБН В.1.2-14:2018") })
    }

    @Test
    fun `демонтаж штукатурки — гіпсова на стелі`() {
        val result = calculatePlasterRemoval(10.0, isGypsum = true, isCeiling = true)
        // продуктивність 5.0 * 0.8 = 4.0 м²/люд·год
        assertEquals(10.0 / 4.0, result.laborHours, 0.01)
        assertEquals(0.25, result.debrisVolumeM3, 0.001)
    }

    @Test
    fun `демонтаж плитки — обсяг і маса сміття`() {
        val result = calculateTileRemoval(9.0)
        assertEquals(9.0 * 0.012, result.debrisVolumeM3, 0.001)
        assertEquals(9.0 / 3.0, result.laborHours, 0.01)
    }

    @Test
    fun `демонтаж стяжки 50 мм — обсяг з коефіцієнтом розпушення`() {
        val result = calculateScreedRemoval(10.0, 50.0)
        assertEquals(10.0 * 0.05 * 1.4, result.debrisVolumeM3, 0.001)
        assertEquals(10.0, result.laborHours, 0.01)
    }

    @Test
    fun `демонтаж підлогового покриття — розрахунок`() {
        val result = calculateFlooringRemoval(23.0)
        assertEquals(23.0 * 0.015, result.debrisVolumeM3, 0.001)
        assertEquals(23.0 / 11.5, result.laborHours, 0.01)
    }

    @Test
    fun `видалення фарби феном — попередження про токсичні випари`() {
        val result = calculatePaintRemoval(
            PaintRemovalParams(10.0, PaintType.OilBased, PaintSubstrate.Plaster, PaintRemovalMethod.HeatGun, 2)
        )
        assertEquals(10.0 / 2.0, result.laborHours, 0.01)
        assertTrue(result.warnings.any { it.contains("токсичні пари") })
    }

    @Test
    fun `видалення водоемульсійної фарби шліфмашиною — без попереджень`() {
        val result = calculatePaintRemoval(
            PaintRemovalParams(10.0, PaintType.WaterBased, PaintSubstrate.Plaster, PaintRemovalMethod.MechanicalGrinder, 2)
        )
        assertEquals(10.0 / 4.0, result.laborHours, 0.01)
        assertTrue(result.warnings.isEmpty())
    }
}

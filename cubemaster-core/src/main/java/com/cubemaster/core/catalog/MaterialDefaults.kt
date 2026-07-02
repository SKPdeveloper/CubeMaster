package com.cubemaster.core.catalog

import com.cubemaster.core.model.*

object MaterialDefaults {

    val catalog: List<MaterialCatalogEntry> = listOf(

        // ---- Стяжки ----
        MaterialCatalogEntry(
            sku = "SCREED_CPS_MANUAL",
            nameUa = "Цементно-піщана стяжка (ручна підготовка)",
            category = MaterialCategory.Screed,
            unit = MeasurementUnit.M3,
            packSize = null,
            densityKgM3 = 2000.0,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 2.0,
                lPerM2 = null,
                minThicknessMm = 30.0,
                maxThicknessPerPassMm = 80.0,
                normativeReference = "ДБН В.2.6-22:2001; технічні картки виробників групи П1",
                minThicknessJustification = "Шар тоншим 30мм не витримує технологічної усадки і розтріскується; " +
                    "40мм мінімум для плаваючої стяжки, 50мм — по утеплювачу"
            )
        ),
        MaterialCatalogEntry(
            sku = "SCREED_CP5_BAGS",
            nameUa = "Суха суміш для стяжки (мішок 25кг)",
            category = MaterialCategory.Screed,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 0.323,
                lPerM2 = null,
                minThicknessMm = 20.0,
                maxThicknessPerPassMm = 60.0,
                normativeReference = "ДБН В.2.6-22:2001; ДСТУ Б В.2.7-126:2011",
                minThicknessJustification = "Мінімум 20мм для сухих сумішей групи П1"
            )
        ),
        MaterialCatalogEntry(
            sku = "SCREED_SLC",
            nameUa = "Самовирівнювальна суміш на цементній основі",
            category = MaterialCategory.Screed,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 1.7,
                lPerM2 = null,
                minThicknessMm = 3.0,
                maxThicknessPerPassMm = 30.0,
                normativeReference = "ДБН В.2.6-22:2001 п. 5.20",
                minThicknessJustification = "Мін. 3мм для фінішного нівелірного шару; 5мм для базового. " +
                    "Відхилення товщини не більше 10% в окремих місцях (п. 5.20)"
            )
        ),
        MaterialCatalogEntry(
            sku = "SCREED_SLG",
            nameUa = "Самовирівнювальна суміш на гіпсовій основі",
            category = MaterialCategory.Screed,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 1.6,
                lPerM2 = null,
                minThicknessMm = 5.0,
                maxThicknessPerPassMm = 50.0,
                normativeReference = "ДБН В.2.6-22:2001; ДСТУ Б В.2.7-126:2011",
                minThicknessJustification = "Мінімум 5мм для гіпсових самовирівнювальних сумішей"
            )
        ),

        // ---- Штукатурки ----
        MaterialCatalogEntry(
            sku = "PLASTER_GYPSUM_MANUAL",
            nameUa = "Гіпсова штукатурка (ручне нанесення)",
            category = MaterialCategory.Plaster,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 0.875,
                lPerM2 = null,
                minThicknessMm = 5.0,
                maxThicknessPerPassMm = 30.0,
                normativeReference = "ДБН В.2.6-22:2001 дод. В (група Ш2)",
                minThicknessJustification = "Мінімум 5мм для гіпсової штукатурки (технічні картки груп Ш2)"
            )
        ),
        MaterialCatalogEntry(
            sku = "PLASTER_GYPSUM_MACHINE",
            nameUa = "Гіпсова штукатурка (машинне нанесення)",
            category = MaterialCategory.Plaster,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 0.845,
                lPerM2 = null,
                minThicknessMm = 8.0,
                maxThicknessPerPassMm = 50.0,
                normativeReference = "ДБН В.2.6-22:2001 дод. В (група Ш2); технічні картки виробників",
                minThicknessJustification = "Мін. 8мм при машинному нанесенні; −3% витрати за рахунок рівномірності"
            )
        ),
        MaterialCatalogEntry(
            sku = "PLASTER_CSM",
            nameUa = "Цементно-піщана штукатурка (ручна)",
            category = MaterialCategory.Plaster,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 1.65,
                lPerM2 = null,
                minThicknessMm = 30.0,
                maxThicknessPerPassMm = 50.0,
                normativeReference = "ДБН В.2.6-22:2001 (група Ш4); СНиП 3.04.01-87",
                minThicknessJustification = "Шар тоншим 30мм для ЦПС сохне нерівномірно і тріскається; " +
                    "понад 50мм — з армувальною сіткою"
            )
        ),

        // ---- Шпаклівка ----
        MaterialCatalogEntry(
            sku = "WALL_PUTTY_FINISH",
            nameUa = "Шпаклівка фінішна",
            category = MaterialCategory.WallFinish,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = 1.1,
                lPerM2 = null,
                minThicknessMm = 1.0,
                maxThicknessPerPassMm = 3.0,
                normativeReference = "Технічні картки виробників",
                minThicknessJustification = "Стандарт 1–2мм; +3% на приміщення з ≥5 кутами"
            )
        ),

        // ---- Ґрунтовка ----
        MaterialCatalogEntry(
            sku = "PRIMER_DEEP",
            nameUa = "Ґрунтовка глибокого проникнення",
            category = MaterialCategory.Primer,
            unit = MeasurementUnit.L,
            packSize = 10.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = null,
                lPerM2 = 0.125,
                minThicknessMm = null,
                maxThicknessPerPassMm = null,
                normativeReference = "Технічні картки виробників",
                minThicknessJustification = "0.10–0.15 л/м²; ×1.5 для пористих основ"
            )
        ),
        MaterialCatalogEntry(
            sku = "PRIMER_CONTACT",
            nameUa = "Ґрунтовка контактна (бетоноконтакт)",
            category = MaterialCategory.Primer,
            unit = MeasurementUnit.L,
            packSize = 5.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = null,
                lPerM2 = 0.35,
                minThicknessMm = null,
                maxThicknessPerPassMm = null,
                normativeReference = "Технічні картки виробників",
                minThicknessJustification = "Для гладких бетонних основ перед штукатуркою"
            )
        ),

        // ---- Гідроізоляція ----
        MaterialCatalogEntry(
            sku = "WATERPROOF_COATING",
            nameUa = "Обмазувальна гідроізоляція (2 шари)",
            category = MaterialCategory.Waterproofing,
            unit = MeasurementUnit.Kg,
            packSize = 20.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = null,
                lPerM2 = 1.5,
                minThicknessMm = null,
                maxThicknessPerPassMm = null,
                normativeReference = "ДБН В.2.6-22:2001 (Г1/Г2)",
                minThicknessJustification = "1.3–1.7 кг/м² за 2 шари + підйом на стіни 20–30 см"
            )
        ),

        // ---- Клей для плитки ----
        MaterialCatalogEntry(
            sku = "TILE_ADHESIVE",
            nameUa = "Клей для керамічної плитки",
            category = MaterialCategory.Adhesive,
            unit = MeasurementUnit.Bag25,
            packSize = 25.0,
            densityKgM3 = null,
            consumptionNorm = ConsumptionNorm(
                kgPerM2PerMm = null,
                lPerM2 = 5.0,
                minThicknessMm = null,
                maxThicknessPerPassMm = null,
                normativeReference = "ДСТУ Б В.2.7-126:2011; технічні картки",
                minThicknessJustification = "4–6 кг/м² для стандартної плитки"
            )
        )
    )

    fun findBySku(sku: String): MaterialCatalogEntry? = catalog.find { it.sku == sku }
}

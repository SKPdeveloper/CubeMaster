package com.example.cubemaster.ui.theme

import androidx.compose.ui.graphics.Color

object CubeMasterColors {
    val red = Color(0xFFCE1126)
    val redDeep = Color(0xFF8B0F1F)
    // 30% прозорість, той самий відтінок — альфа йде першим байтом (0xAARRGGBB),
    // не останнім: Color(Long) робить (value shl 32), тож зайві біти в кінці
    // обрізаються і колір "зʼїжджає" на випадковий RGB, якщо альфу дописати в хвіст.
    val redMuted = Color(0x4DCE1126)
    // World glow для тіні картки в темній темі — м'якший за redMuted.
    val redGlow = Color(0x33CE1126)
    val white = Color(0xFFFFFFFF)
    val linen = Color(0xFFF7F3EC)
    // Глибший OLED-чорний — краще тримає контраст на реальних AMOLED-екранах
    // будівельників на об'єкті, ніж попередній #1C1C1E.
    val graphite = Color(0xFF0A0A0C)
    val graphiteSoft = Color(0xFF16161A)
    val graphiteMid = Color(0xFF2C2C35)
    val gold = Color(0xFFC9A227)
    val textPrimary = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF6C6C70)
    val textOnDark = Color(0xFFF7F3EC)
    val divider = Color(0xFFE5E0D8)
    val success = Color(0xFF34C759)
    val warning = Color(0xFFFF9500)
    val error = Color(0xFFFF3B30)
}

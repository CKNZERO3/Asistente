package com.miasistente.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
class IrisPalette(
    val isDark: Boolean,
    val background: Brush,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val card: Color,
    val outline: Color,
    val composer: Brush,
    val assistantBubble: Color,
    val scrim: Color
)

val IrisAccentGradient: Brush = Brush.horizontalGradient(
    listOf(Color(0xFFB996FF), Color(0xFF8056E8))
)

val IrisDarkPalette = IrisPalette(
    isDark = true,
    background = Brush.verticalGradient(
        listOf(Color(0xFF080C21), Color(0xFF12112E), Color(0xFF24133D))
    ),
    text = Color(0xFFF4F0FF),
    muted = Color(0xFFB4A9D1),
    accent = Color(0xFFA78BFA),
    card = Color(0xFF292342),
    outline = Color(0x1AFFFFFF),
    composer = Brush.horizontalGradient(listOf(Color(0xFF30234D), Color(0xFF38264F))),
    assistantBubble = Color(0xFF292342),
    scrim = Color.Black.copy(alpha = 0.55f)
)

val IrisLightPalette = IrisPalette(
    isDark = false,
    background = Brush.verticalGradient(
        listOf(Color(0xFFF8F6FF), Color(0xFFEFEAFB), Color(0xFFE6DDF8))
    ),
    text = Color(0xFF1C1638),
    muted = Color(0xFF6E6491),
    accent = Color(0xFF7B4FE0),
    card = Color.White,
    outline = Color(0x1F1C1638),
    composer = Brush.horizontalGradient(listOf(Color.White, Color(0xFFF6F2FF))),
    assistantBubble = Color.White,
    scrim = Color.Black.copy(alpha = 0.35f)
)

val LocalIrisPalette = staticCompositionLocalOf { IrisDarkPalette }

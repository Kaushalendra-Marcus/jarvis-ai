package ai.jarvis.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

// ---- Jarvis brand — from ClayRobot studio component (single source of truth) ----
// ClayRobot defaults: clay #B5A1FF is the primary, visor #22225E is the ink,
// eyes #FFFFFF glow on the screen, cyclorama #909EA9 → #CCD0D3, shadow #2E4D6A.
internal val JarvisPrimary = Color(0xFF2F7CF6)
internal val JarvisPrimaryDeep = Color(0xFF1D5CC4)
internal val JarvisOnPrimary = Color(0xFFFFFFFF)
internal val JarvisPrimaryMist = Color(0xFFDCE7FF)
internal val JarvisVisor = Color(0xFF22225E)
internal val JarvisEye = Color(0xFFFFFFFF)
internal val JarvisSkyTop = Color(0xFF909EA9)
internal val JarvisSkyBottom = Color(0xFFCCD0D3)
internal val JarvisShadow = Color(0xFF2E4D6A)
// FoldingParticleShell accent for the speck field.
internal val JarvisSpeck = Color(0xFFC9C7DA)

internal val MidnightBg = Color(0xFF060A13)
internal val MidnightStage = Color(0xFF0B1220)
internal val MidnightCard = Color(0xFF101A2E)
internal val MidnightCardAlt = Color(0xFF14213A)
internal val MidnightLine = Color(0xFF1E2C48)

internal val LimePop = JarvisPrimary
internal val LimeDeep = JarvisPrimaryDeep
internal val LimeInk = JarvisOnPrimary
internal val LimeMist = JarvisPrimaryMist

internal val OrbAqua = Color(0xFF7DF9FF)
internal val OrbBlue = Color(0xFF5B8CFF)
internal val OrbTeal = Color(0xFF0F766E)
internal val OrbMist = Color(0xFF9FE8FF)

internal val InkHigh = Color(0xFFF2F6EC)
internal val InkMid = Color(0xFFA8B3C7)
internal val InkLow = Color(0xFF6B7690)

internal val PaperBg = Color(0xFFF1F4ED)
internal val PaperCard = Color(0xFFFFFFFF)
internal val PaperLine = Color(0xFFE2E8DF)
internal val PineInk = Color(0xFF0C211F)
internal val PineDeep = Color(0xFF0F766E)

// Legacy aliases — kept so old call sites keep compiling during the rebrand.
internal val JarvisMidnight = MidnightBg
internal val JarvisSurfaceDark = MidnightCard
internal val JarvisSurfaceVariantDark = MidnightCardAlt
internal val JarvisLime = LimePop
internal val JarvisLimeDark = LimeDeep
internal val JarvisLimeSoft = LimeMist
internal val JarvisTeal = OrbTeal
internal val JarvisTealLight = OrbMist
internal val JarvisClay = Color(0xFF2F7CF6)
internal val JarvisClayLight = Color(0xFFDCE7FF)

internal val BrandIndigo = PineDeep
internal val BrandIndigoDark = PineInk
internal val BrandIndigoSoft = LimeMist
internal val BrandTeal = OrbTeal

// ---- Status colours ----
val SuccessGreen = Color(0xFF34D399)
val WarningOrange = Color(0xFFFBBF24)
val ErrorRed = Color(0xFFFF6B6B)

// ---- Back-compat semantic tokens (old screens keep compiling) ----
@androidx.compose.runtime.Composable
fun primaryBlue(): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
val PrimaryBlue: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.primary
val SecondaryTeal: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.secondary
val BackgroundLight: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.background
val SurfaceLight: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.surfaceVariant
val TextPrimary: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val ToolBadgeBackground: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.surfaceVariant
val SurfaceBorder: androidx.compose.ui.graphics.Color @androidx.compose.runtime.Composable get() = MaterialTheme.colorScheme.outlineVariant

// ---- Category accents (no purple) ----
val CategoryWork = Color(0xFF38BDF8)
val CategoryPreference = Color(0xFF2F7CF6)
val CategoryPersonal = Color(0xFF7DF9FF)
val CategoryGeneral = Color(0xFF94A3B8)

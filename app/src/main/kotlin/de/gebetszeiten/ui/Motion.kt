package de.gebetszeiten.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** False, wenn der System-Animationsskalierungsfaktor 0 ist ("Animationen aus"). */
fun animationsEnabled(scale: Float): Boolean = scale != 0f

/** Liest die globale Animationsskalierung und meldet, ob animiert werden soll. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return animationsEnabled(scale)
}

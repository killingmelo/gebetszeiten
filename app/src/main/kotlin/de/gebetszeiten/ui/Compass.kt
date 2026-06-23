package de.gebetszeiten.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.gebetszeiten.prayer.QiblaMath

/** Geräte-Azimut (0..360 von Norden), geglättet; null wenn kein Rotationssensor. */
@Composable
fun rememberDeviceAzimuth(): Float? {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var azimuth by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(rotationSensor) {
        if (rotationSensor == null) {
            onDispose { }
        } else {
            val matrix = FloatArray(9)
            val remapped = FloatArray(9)
            val orientation = FloatArray(3)
            // Fix 2: read display rotation ONCE per effect cycle instead of every sensor event.
            val (axisX, axisY) = when (displayRotation(context)) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)
                    SensorManager.getOrientation(remapped, orientation)
                    val deg = QiblaMath.normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
                    azimuth = lowPass(deg, azimuth)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    // Sensor vorhanden, aber noch keine Messung → provisorisch 0; gar kein Sensor → null.
    return if (rotationSensor == null) null else (azimuth ?: 0f)
}

/** Zirkulärer Tiefpass (Wraparound-sicher) gegen Zittern. */
private fun lowPass(new: Float, old: Float?): Float {
    if (old == null) return new
    var delta = new - old
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return QiblaMath.normalizeDegrees(old + 0.15f * delta)
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

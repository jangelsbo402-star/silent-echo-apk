package com.xfnyty.silentecho

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMag = false

    private var lockedAzimuth: Float? = null
    private var currentAzimuth = 0f
    private var currentPitch = 0f
    private var pulseCount = 0

    private lateinit var tvZone: TextView
    private lateinit var tvGuidance: TextView
    private lateinit var tvVector: TextView
    private lateinit var tvCymatic: TextView
    private lateinit var btnPulse: Button
    private lateinit var btnLock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvZone = findViewById(R.id.tvZone)
        tvGuidance = findViewById(R.id.tvGuidance)
        tvVector = findViewById(R.id.tvVector)
        tvCymatic = findViewById(R.id.tvCymatic)
        btnPulse = findViewById(R.id.btnPulse)
        btnLock = findViewById(R.id.btnLock)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        btnPulse.setOnClickListener {
            pulseCount += 1
            triggerHaptic(45L)
            tvCymatic.text = "Cymatic Node: Pulse #$pulseCount @ 7.83 Hz · Tilt: ${tiltLabel(currentPitch)}"
        }

        btnLock.setOnClickListener {
            if (lockedAzimuth == null) {
                lockedAzimuth = currentAzimuth
                btnLock.text = "Lock Vector: ${lockedAzimuth!!.toInt()} deg"
                tvZone.text = "ZONE: VECTOR LOCKED"
            } else {
                lockedAzimuth = null
                btnLock.text = "Lock Vector: OFF"
                tvZone.text = "ZONE: SCANNING"
            }
            refreshSpatialReadout()
        }
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasMag = true
            }
        }

        if (hasGravity && hasMag) {
            val rMat = FloatArray(9)
            val iMat = FloatArray(9)
            if (SensorManager.getRotationMatrix(rMat, iMat, gravity, geomagnetic)) {
                val orient = FloatArray(3)
                SensorManager.getOrientation(rMat, orient)
                val deg = ((Math.toDegrees(orient[0].toDouble()).toFloat()) + 360f) % 360f
                val pitchDeg = Math.toDegrees(orient[1].toDouble()).toFloat()
                currentAzimuth = deg
                currentPitch = pitchDeg
                refreshSpatialReadout()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun refreshSpatialReadout() {
        val headingInt = currentAzimuth.toInt()
        val cardinal = cardinalName(currentAzimuth)
        val tilt = tiltLabel(currentPitch)
        val leftDeg = (headingInt + 270) % 360
        val rightDeg = (headingInt + 90) % 360

        val locked = lockedAzimuth
        if (locked != null) {
            val delta = (currentAzimuth - locked + 540f) % 360f - 180f
            val absDelta = abs(delta)
            tvZone.text = if (absDelta <= 15f) "ZONE: ALIGNED PORTAL" else "ZONE: DRIFT ${absDelta.toInt()} deg"
            tvGuidance.text = when {
                absDelta <= 15f -> "Aligned with locked vector (${locked.toInt()} deg), $tilt."
                delta < 0f -> "Turn right toward locked vector (${locked.toInt()} deg), $tilt."
                else -> "Turn left toward locked vector (${locked.toInt()} deg), $tilt."
            }
        } else {
            tvZone.text = "ZONE: ACTIVE SWEEP"
            tvGuidance.text = "Facing $cardinal ($headingInt deg), $tilt."
        }

        tvVector.text = "LEFT ${leftDeg} deg | AHEAD ${headingInt} deg ($cardinal) | RIGHT ${rightDeg} deg"
        tvCymatic.text = "Cymatic Node: Pulse #$pulseCount @ 7.83 Hz · Tilt: $tilt (${currentPitch.toInt()} deg)"
    }

    private fun cardinalName(deg: Float): String {
        val dirs = arrayOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
        val idx = (((deg % 360f) + 22.5f) / 45f).toInt() % 8
        return dirs[idx]
    }

    private fun tiltLabel(pitch: Float): String = when {
        pitch >= 18f -> "UP"
        pitch <= -18f -> "DOWN"
        else -> "LEVEL"
    }

    private fun triggerHaptic(ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vib.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }
}

package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

enum class HapticStyle {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    TOGGLE
}

/**
 * Reliable, low-latency haptic feedback helper across Android API 24–36.
 * Uses hardware Vibrator / VibratorManager with amplitude-control checks so
 * tactile feedback works consistently even on devices where View.performHapticFeedback
 * is muted or barely perceptible, falling back cleanly if no vibrator hardware exists.
 */
object HapticsHelper {

    fun perform(
        context: Context,
        view: View,
        enabled: Boolean,
        style: HapticStyle
    ) {
        if (!enabled) return

        val vibrated = runCatching {
            val vibrator = resolveVibrator(context) ?: return@runCatching false
            if (!vibrator.hasVibrator()) return@runCatching false

            val (durationMs, amplitude) = when (style) {
                HapticStyle.POSITIVE -> 18L to 170
                HapticStyle.NEGATIVE -> 28L to 225
                HapticStyle.NEUTRAL -> 12L to 130
                HapticStyle.TOGGLE -> 15L to 150
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val safeAmplitude = if (vibrator.hasAmplitudeControl()) {
                    amplitude
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, safeAmplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
            true
        }.getOrDefault(false)

        if (!vibrated) {
            runCatching {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

package com.mlue.app.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberHabitInteractionFeedback(
    soundsEnabled: Boolean,
    hapticsEnabled: Boolean
): HabitInteractionFeedback {
    val context = LocalContext.current
    val view = LocalView.current
    
    // Create the controller once per composition
    val feedback = remember { HabitInteractionFeedback(context, view) }
    
    // Keep properties updated without recreating the controller
    feedback.soundsEnabled = soundsEnabled
    feedback.hapticsEnabled = hapticsEnabled
    
    // Manage ToneGenerator lifecycle securely within this composition
    DisposableEffect(feedback) {
        feedback.initialize()
        onDispose {
            feedback.release()
        }
    }
    
    return feedback
}

class HabitInteractionFeedback(
    private val context: Context,
    private val view: View
) {
    var soundsEnabled = true
    var hapticsEnabled = true
    
    private var toneGenerator: ToneGenerator? = null
    private var lastTriggerTime = 0L
    
    fun initialize() {
        if (toneGenerator != null) return
        try {
            // Volume at 75% — premium, noticeable, calm.
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        } catch (e: Exception) {
            // Silently ignore: ToneGenerator unavailable on some low-memory devices
        }
    }
    
    fun release() {
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore release errors
        } finally {
            toneGenerator = null
        }
    }
    
    fun triggerCompletionFeedback() {
        val now = SystemClock.elapsedRealtime()
        // Debounce by 300ms to completely prevent double-fire glitches from rapid recompositions/taps
        if (now - lastTriggerTime < 300L) return
        lastTriggerTime = now
        
        if (hapticsEnabled) {
            // Native View haptics are much more reliable on Samsung/OneUI than Compose's LongPress.
            // CONFIRM provides a crisp premium click on API 30+, fallback to VIRTUAL_KEY.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        
        if (soundsEnabled) {
            try {
                // TONE_PROP_ACK produces a warm, crisp confirmation click.
                // 100ms duration
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            } catch (e: Exception) {
                // Ignore transient audio focus/resource errors
            }
        }
    }
}

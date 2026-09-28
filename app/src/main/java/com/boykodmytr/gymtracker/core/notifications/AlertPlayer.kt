package com.boykodmytr.gymtracker.core.notifications

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** In-app timer alert (sound + vibration) honouring the user's settings. */
@Singleton
class AlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun play() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            withContext(Dispatchers.Main) {
                if (settings.timerSound) playSound()
                if (settings.timerVibration) vibrate()
            }
        }
    }

    private fun playSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }

    private companion object {
        val PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)
    }
}

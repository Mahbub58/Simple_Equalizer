package com.envobyte.simpleequalizer.viewModel

import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import com.envobyte.simpleequalizer.model.Strength
import com.envobyte.simpleequalizer.utils.DynamicsProcessingService


private const val TAG = "MainViewModel"

class MainViewModel : ViewModel() {

    // Don't initialize DynamicsProcessingService in constructor - it will crash!
    @RequiresApi(Build.VERSION_CODES.P)
    private var _dynamicProcessing = DynamicsProcessingService(
        audioSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE,
        channelCount = 2,
        preEqInUse = true,
        preEqBandCount = 10,
        mbcInUse = true,
        mbcBandCount = 0,
        postEqInUse = true,
        postEqBandCount = 10,
        limiterInUse = false
    )


    private val _equalizerGains = mutableListOf(0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F)
    private var _bassBoostStrength = Strength()
    private var _loudnessStrength = Strength()

    private var isEqualizerEnabled = false
    private var isBassBoostEnabled = false
    private var isLoudnessEnabled = false


    /**
     * Equalizer Modification Codes
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setEqualizerEnabled(isEnabled: Boolean) {
        isEqualizerEnabled = isEnabled
        val gain = if(!isEqualizerEnabled) {
            listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        } else {
            _equalizerGains
        }
        for(index in gain.indices){
            _dynamicProcessing.setPreEqBandByIndex(index, gain[index])
        }
    }
    @RequiresApi(Build.VERSION_CODES.P)
    fun setEqualizerGainByIndex(index: Int, gain: Float) {
        _equalizerGains[index] = gain

        if(isEqualizerEnabled) {
            _dynamicProcessing.setPreEqBandByIndex(index, gain)
        }
    }

    /**
     * BassBoost Modification Codes
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setBassBoostEnabled(isEnabled: Boolean) {
        isBassBoostEnabled = isEnabled
        _bassBoostStrength.currentStrength =
            if(isBassBoostEnabled) _bassBoostStrength.savedStrength
            else 0f

        setPostEqStrength()
    }
    @RequiresApi(Build.VERSION_CODES.P)
    fun setBassBoostStrength(strength: Int) {
        _bassBoostStrength.savedStrength = strength.toFloat() * 15 / 100
        _bassBoostStrength.currentStrength = _bassBoostStrength.savedStrength

        if(isBassBoostEnabled)
            setPostEqStrength()
    }

    /**
     * Loudness Modification Codes
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setLoudnessEnabled(isEnabled: Boolean) {
        isLoudnessEnabled = isEnabled
        _loudnessStrength.currentStrength =
            if(isLoudnessEnabled) _loudnessStrength.savedStrength
            else 0f

        setPostEqStrength()
    }
    @RequiresApi(Build.VERSION_CODES.P)
    fun setLoudnessStrength(strength: Int) {
        _loudnessStrength.savedStrength = strength.toFloat() * 15 / 100
        _loudnessStrength.currentStrength = _loudnessStrength.savedStrength

        if(isLoudnessEnabled)
            setPostEqStrength()

    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setPostEqStrength() {
        _dynamicProcessing.setPostEqStrength(_bassBoostStrength.currentStrength, _loudnessStrength.currentStrength)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun releaseEqualizer() {
        if(_dynamicProcessing.dynamicsProcessing != null)
            _dynamicProcessing.dynamicsProcessing?.release()
    }

    fun getGainForIndex(index: Int): Float {
        return _equalizerGains.getOrNull(index) ?: 0f
    }
}
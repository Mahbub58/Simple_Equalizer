package com.envobyte.simpleequalizer
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.envobyte.simpleequalizer.repository.presetList
import com.envobyte.simpleequalizer.repository.presets
import com.envobyte.simpleequalizer.ui.theme.SimpleEqualizerTheme
import com.envobyte.simpleequalizer.utils.ForegroundService
import com.envobyte.simpleequalizer.viewModel.MainViewModel


import kotlin.math.*

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var visualizer: Visualizer
    private lateinit var audioManager: AudioManager
    private var currentVolume = 0
    private var isNotMuted = true
    private var returningFromSettings = false
    private var audioSessionId = 0
    private val mainViewModel: MainViewModel by viewModels()

    private val permissions = when(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        true -> arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.RECORD_AUDIO
        )
        false -> arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Log.i("test", "multiplePermissionLauncher.launch")
            val resultPermission = it.all { map -> map.value }
            if (!resultPermission) {
                Toast.makeText(this, "All Permissions must be allowed!", Toast.LENGTH_SHORT).show()

            } else init()
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            SimpleEqualizerTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    EqualizerScreen()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkPermissions() {
        Log.i("test", "checkPermissions")
        when {
            (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) -> init()
            (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO)
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) ->{}
            else -> multiplePermissionLauncher.launch(permissions)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ResourceType")
    private fun init() {
        Log.i("test", "init")
        audioSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE
        val serviceIntent = Intent(this, ForegroundService::class.java)
        startForegroundService(serviceIntent)
        initAudioManager()

    }

// Replace your existing init() method with this:

//    @SuppressLint("ServiceCast")
//    private fun init() {
//        Log.i(TAG, "Initializing app components")
//        try {
//            // Initialize audio manager first
//            initAudioManager()
//
//            // Generate proper audio session ID
//            audioSessionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                audioManager.generateAudioSessionId()
//            } else {
//                AudioManager.AUDIO_SESSION_ID_GENERATE
//            }
//            Log.i(TAG, "Generated audio session ID: $audioSessionId")
//
//            // Initialize ViewModel with audio session ID (this is the key fix!)
//            mainViewModel.initializeWithAudioSession(audioSessionId)
//
//            // Start foreground service
//            val serviceIntent = Intent(this, ForegroundService::class.java)
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                startForegroundService(serviceIntent)
//            } else {
//                startService(serviceIntent)
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error during initialization", e)
//            Toast.makeText(this, "Failed to initialize audio components: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }


    private fun initAudioManager() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toString())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        if (returningFromSettings) {
            checkPermissions()
        }
        returningFromSettings = false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        super.onDestroy()
        if (::visualizer.isInitialized) {
            visualizer.release()
        }
        val serviceIntent = Intent(this, ForegroundService::class.java)
        stopService(serviceIntent)
        mainViewModel.releaseEqualizer()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Composable
    private fun EqualizerScreen() {
        var equalizerEnabled by remember { mutableStateOf(false) }
        var bassBoostEnabled by remember { mutableStateOf(false) }
        var loudnessEnabled by remember { mutableStateOf(false) }
        var isMuted by remember { mutableStateOf(false) }
        var selectedPreset by remember { mutableStateOf(0) }
        var volumeLevel by remember { mutableStateOf(50f) }
        var bassBoostStrength by remember { mutableStateOf(0f) }
        var loudnessStrength by remember { mutableStateOf(0f) }


        // Equalizer gain values for 10 bands
        var equalizerGains by remember {
            mutableStateOf(List(10) { 0f })
        }

        val frequencies = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Audio Equalizer",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )



            // Main Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Switches Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SwitchWithLabel(
                            label = "EQ",
                            checked = equalizerEnabled,
                            onCheckedChange = {
                                equalizerEnabled = it
                                mainViewModel.setEqualizerEnabled(it)
                            }
                        )
                        SwitchWithLabel(
                            label = "Bass",
                            checked = bassBoostEnabled,
                            onCheckedChange = {
                                bassBoostEnabled = it
                                mainViewModel.setBassBoostEnabled(it)
                            }
                        )
                        SwitchWithLabel(
                            label = "Loud",
                            checked = loudnessEnabled,
                            onCheckedChange = {
                                loudnessEnabled = it
                                mainViewModel.setLoudnessEnabled(it)
                            }
                        )
                        SwitchWithLabel(
                            label = "Mute",
                            checked = isMuted,
                            onCheckedChange = {
                                isMuted = it
                                isNotMuted = !it
                                if (isNotMuted) {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
                                } else {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                                }
                            }
                        )
                    }
                }
            }

            // Preset Spinner
            PresetDropdown(
                selectedPreset = selectedPreset,
                onPresetSelected = { index ->
                    selectedPreset = index
                    val newGains = presets[index].map.toList().map { it.second }
                    equalizerGains = newGains
                }
            )

            // Volume Control
            CircularSliderCompose(
                value = volumeLevel,
                onValueChange = { value ->
                    volumeLevel = value
                    if (isNotMuted) {
                        currentVolume = (value * 15 / 100).toInt()
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            currentVolume,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                },
                label = "Volume"
            )

            // Bass Boost and Loudness Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CircularSliderCompose(
                    value = bassBoostStrength,
                    onValueChange = { value ->
                        bassBoostStrength = value
                        mainViewModel.setBassBoostStrength(value.toInt())
                    },
                    label = "Bass",
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                CircularSliderCompose(
                    value = loudnessStrength,
                    onValueChange = { value ->
                        loudnessStrength = value
                        mainViewModel.setLoudnessStrength(value.toInt())
                    },
                    label = "Loud",
                    modifier = Modifier.weight(1f)
                )
            }

            // Equalizer Bands
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text(
                        text = "Equalizer Bands",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(frequencies) { index, frequency ->
                            EqualizerBandSlider(
                                frequency = frequency,
                                gain = equalizerGains[index],
                                onGainChange = { gain ->
                                    val newGains = equalizerGains.toMutableList()
                                    newGains[index] = gain
                                    equalizerGains = newGains
                                    mainViewModel.setEqualizerGainByIndex(index, gain)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SwitchWithLabel(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.Green,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PresetDropdown(
        selectedPreset: Int,
        onPresetSelected: (Int) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = presetList[selectedPreset],
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Presets", color = Color.White) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    presetList.forEachIndexed { index, preset ->
                        DropdownMenuItem(
                            text = { Text(preset) },
                            onClick = {
                                onPresetSelected(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CircularSliderCompose(
        value: Float,
        onValueChange: (Float) -> Unit,
        label: String,
        modifier: Modifier = Modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.3f))
            ) {
                CircularSlider(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.size(70.dp)
                )

                Text(
                    text = "${value.toInt()}",
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun CircularSlider(
        value: Float,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // This is a simplified circular slider implementation
        // For a full implementation, you might want to use a third-party library
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Green,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = modifier
        )
    }

    @Composable
    private fun EqualizerBandSlider(
        frequency: String,
        gain: Float,
        onGainChange: (Float) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = frequency,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(40.dp)
                )

                Slider(
                    value = gain + 15f, // Convert from -15 to +15 range to 0 to 30
                    onValueChange = { value ->
                        onGainChange(value - 15f) // Convert back to -15 to +15
                    },
                    valueRange = 0f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${gain.toInt()}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }


}
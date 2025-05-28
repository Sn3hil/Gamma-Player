package com.example.gammaplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.video.VideoSize
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.math.abs
import android.widget.FrameLayout

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var audioManager: AudioManager
    private var originalBrightness: Float = 0.5f
    private val hideControllerRunnable = Runnable {
        playerView.hideController()
    }

    private lateinit var brightnessControl: LinearLayout
    private lateinit var volumeControl: LinearLayout
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private var brightnessControlHideHandler = Handler(Looper.getMainLooper())
    private var volumeControlHideHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        brightnessControl.visibility = View.GONE
        volumeControl.visibility = View.GONE
    }

    private val aspectModes = listOf(
        Pair("Fit (Original)", AspectRatioFrameLayout.RESIZE_MODE_FIT),
        Pair("Fill Screen", AspectRatioFrameLayout.RESIZE_MODE_FILL),
        Pair("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
        Pair("Fixed Width", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
        Pair("Fixed Height", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
    )
    private var currentAspectIndex = 0

    private var playWhenReady = true
    private var playbackPosition = 0L
    private var maxVolume = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var initialX = 0f
    private var initialY = 0f
    private var initialVolume = 0
    private var initialBrightness = 0f
    private var gestureType = GestureType.NONE
    private var isSeeking = false

    private enum class GestureType {
        NONE, SEEKING, VOLUME, BRIGHTNESS
    }

    companion object {
        private const val BRIGHTNESS_SENSITIVITY = 200f
        private const val VOLUME_SENSITIVITY = 200f
        private const val MIN_GESTURE_DISTANCE = 20f
        private val gson = Gson()
        private val videoSeekCache = mutableMapOf<String, Float>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        supportActionBar?.hide()

        enableFullScreen()

        // Set up your custom buttons
        val customBtnBack = findViewById<ImageButton>(R.id.customBtnBack)
        val customBtnMenu = findViewById<ImageButton>(R.id.customBtnMenu)
        val customTopButtons = findViewById<LinearLayout>(R.id.customTopButtons)

        customBtnBack.setOnClickListener {
            finish()
        }

        customBtnMenu.setOnClickListener {
            playerView.showController()  // Just show/hide the controller when menu button is clicked
            // Keep the controller visible for the default timeout
            playerView.removeCallbacks(hideControllerRunnable)
            playerView.postDelayed(hideControllerRunnable, playerView.controllerShowTimeoutMs.toLong())
        }

        playerView = findViewById(R.id.playerView)
        playerView.controllerShowTimeoutMs = 2500
        playerView.controllerHideOnTouch = false

        // Sync custom buttons visibility with controller visibility
        playerView.setControllerVisibilityListener { visibility ->
            customTopButtons.visibility = visibility
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        fetchOriginalBrightness()

        val videoPath = intent.getStringExtra("videoPath") ?: finish()
        val seekPosition = loadSeekPosition(videoPath.toString())
        initializePlayer(videoPath.toString(), seekPosition)

        // Initially show the controller and buttons
        playerView.showController()
        customTopButtons.visibility = View.VISIBLE

        // Auto-hide after a delay
        playerView.postDelayed({
            playerView.hideController()
            customTopButtons.visibility = View.GONE
        }, playerView.controllerShowTimeoutMs.toLong())

        setupControlOverlays()
        setupGestureDetection()

        // Restore aspect ratio if saved
        savedInstanceState?.let {
            currentAspectIndex = it.getInt("aspectRatioIndex", 0)
        }
    }

    private fun setupControllerButtons() {
        // Find the controller UI elements using player.findViewById
        val btnMenu = playerView.findViewById<ImageButton>(R.id.btnMenu)
        val menuContainer = playerView.findViewById<LinearLayout>(R.id.btnMenu)
        val aspectButton = playerView.findViewById<ImageButton>(R.id.aspectButton)

        // Set up menu button click listener
        btnMenu?.setOnClickListener {
            if (menuContainer != null) {
                // Toggle menu visibility
                if (menuContainer.visibility == View.VISIBLE) {
                    menuContainer.visibility = View.GONE
                } else {
                    menuContainer.visibility = View.VISIBLE

                    // Auto-hide the menu after delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        menuContainer.visibility = View.GONE
                    }, 5000)
                }

                // Keep the controller visible while menu is shown
                if (menuContainer.visibility == View.VISIBLE) {
                    playerView.removeCallbacks(hideControllerRunnable)
                    playerView.postDelayed(hideControllerRunnable, 5000)
                }
            } else {
                Log.e("PlayerActivity", "menuContainer is null")
                Toast.makeText(this, "Menu system error", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up aspect ratio button click listener
        aspectButton?.setOnClickListener {
            cycleAspectRatio()
        }
    }

    @SuppressLint("InlinedApi")
    private fun enableFullScreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    private fun fetchOriginalBrightness() {
        originalBrightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Settings.SettingNotFoundException) {
            0.5f
        }
    }

    private fun initializePlayer(path: String, seekPosition: Float) {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(Uri.parse(path))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.seekTo(seekPosition.toLong())
        exoPlayer.playWhenReady = playWhenReady

        // Setup aspect ratio button
        playerView.findViewById<ImageButton>(R.id.aspectButton)?.setOnClickListener {
            cycleAspectRatio()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    playerView.showController()
                    playerView.postDelayed({ playerView.hideController() }, playerView.controllerShowTimeoutMs.toLong())
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                super.onVideoSizeChanged(videoSize)
                setOrientationBasedOnVideo()
            }
        })
    }

    private fun setupGestureDetection() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerVisible) {
                    playerView.hideController()
                } else {
                    playerView.showController()
                    // Remove any pending callbacks
                    playerView.removeCallbacks(hideControllerRunnable)
                    // Post a fresh delayed hide
                    playerView.postDelayed(hideControllerRunnable, playerView.controllerShowTimeoutMs.toLong())
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (e.x < playerView.width / 2) {
                    exoPlayer.seekBack()
                } else {
                    exoPlayer.seekForward()
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            val gestureHandled = gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> onTouchDown(event)
                MotionEvent.ACTION_MOVE -> onTouchMove(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchUp(gestureHandled)
            }

            true
        }
    }

    private fun onTouchDown(event: MotionEvent) {
        initialX = event.x
        initialY = event.y
        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        initialBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
        gestureType = GestureType.NONE
        isSeeking = false
    }

    private fun onTouchMove(event: MotionEvent) {
        val deltaX = event.x - initialX
        val deltaY = event.y - initialY

        // First determine gesture type if not already set
        if (gestureType == GestureType.NONE &&
            (abs(deltaX) > MIN_GESTURE_DISTANCE || abs(deltaY) > MIN_GESTURE_DISTANCE)) {

            gestureType = when {
                abs(deltaX) > abs(deltaY) * 1.5 -> GestureType.SEEKING
                abs(deltaY) > abs(deltaX) * 1.5 -> {
                    if (initialX < screenWidth / 2) {
                        brightnessControl.visibility = View.VISIBLE
                        brightnessControlHideHandler.removeCallbacks(hideControlsRunnable)
                        GestureType.BRIGHTNESS
                    } else {
                        volumeControl.visibility = View.VISIBLE
                        volumeControlHideHandler.removeCallbacks(hideControlsRunnable)
                        GestureType.VOLUME
                    }
                }
                else -> GestureType.NONE
            }
        }

        // Handle the detected gesture
        when (gestureType) {
            GestureType.SEEKING -> {
                if (!isSeeking) {
                    isSeeking = true
                    playerView.showController()
                }
                handleSeeking(deltaX, event)
            }
            GestureType.BRIGHTNESS -> {
                val percentChange = deltaY / screenHeight
                val newBrightness = (initialBrightness - percentChange).coerceIn(0.01f, 1.0f)
                brightnessSeekBar.progress = (newBrightness * 100).toInt()
                handleBrightness(deltaY)
            }
            GestureType.VOLUME -> {
                val percentChange = deltaY / screenHeight
                val volumeChange = (maxVolume * -percentChange).toInt()
                val newVolume = (initialVolume + volumeChange).coerceIn(0, maxVolume)
                volumeSeekBar.progress = newVolume
                handleVolume(deltaY)
            }
            else -> {}
        }
    }

    private fun setupControlOverlays() {
        brightnessControl = findViewById(R.id.brightnessControl)
        volumeControl = findViewById(R.id.volumeControl)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)

        // Initialize seek bars
        brightnessSeekBar.max = 100
        brightnessSeekBar.progress = (initialBrightness * 100).toInt()
        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = initialVolume

        // Set up listeners
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newBrightness = progress / 100f
                    window.attributes = window.attributes.apply {
                        screenBrightness = newBrightness.coerceIn(0.01f, 1.0f)
                    }
                    initialBrightness = newBrightness // Update initial value
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                brightnessControlHideHandler.removeCallbacks(hideControlsRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                brightnessControlHideHandler.postDelayed(hideControlsRunnable, 2000)
            }
        })

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    initialVolume = progress // Update initial value
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                volumeControlHideHandler.removeCallbacks(hideControlsRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                volumeControlHideHandler.postDelayed(hideControlsRunnable, 2000)
            }
        })
    }

    private fun handleSeeking(deltaX: Float, event: MotionEvent) {
        if (abs(deltaX) > 50) {
            if (deltaX > 0) {
                exoPlayer.seekForward()
            } else {
                exoPlayer.seekBack()
            }
            initialX = event.x
        }
    }

    private fun handleVolume(deltaY: Float) {
        if (abs(deltaY) > 5) {
            val percentChange = deltaY / screenHeight
            val volumeChange = (maxVolume * -percentChange).toInt()
            val newVolume = (initialVolume + volumeChange).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        }
    }

    private fun handleBrightness(deltaY: Float) {
        if (abs(deltaY) > 5) {
            val percentChange = deltaY / screenHeight
            val newBrightness = (initialBrightness - percentChange).coerceIn(0.01f, 1.0f)
            window.attributes = window.attributes.apply {
                screenBrightness = newBrightness
            }
        }
    }

    private fun onTouchUp(gestureHandled: Boolean) {
        when (gestureType) {
            GestureType.BRIGHTNESS -> {
                brightnessControlHideHandler.postDelayed(hideControlsRunnable, 2000)
            }
            GestureType.VOLUME -> {
                volumeControlHideHandler.postDelayed(hideControlsRunnable, 2000)
            }
            GestureType.SEEKING -> {
                if (isSeeking) {
                    playerView.postDelayed({ playerView.hideController() }, playerView.controllerShowTimeoutMs.toLong())
                }
            }
            else -> {}
        }

        gestureType = GestureType.NONE
        isSeeking = false
    }

    private fun cycleAspectRatio() {
        currentAspectIndex = (currentAspectIndex + 1) % aspectModes.size
        val (label, mode) = aspectModes[currentAspectIndex]
        
        playerView.resizeMode = mode
    }

    override fun onStart() {
        super.onStart()
        if (::playerView.isInitialized && !::exoPlayer.isInitialized) {
            intent.getStringExtra("videoPath")?.let { initializePlayer(it, 0f) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::playerView.isInitialized) {
            playerView.showController()
            playerView.postDelayed({ playerView.hideController() }, playerView.controllerShowTimeoutMs.toLong())
        }
        if (::exoPlayer.isInitialized) {
            exoPlayer.playWhenReady = playWhenReady
        }
    }

    override fun onPause() {
        super.onPause()
        if (::exoPlayer.isInitialized) {
            playbackPosition = exoPlayer.currentPosition
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.playWhenReady = false
            saveSeekPosition(intent.getStringExtra("videoPath") ?: "", playbackPosition.toFloat())
        }
        resetBrightness()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        if (::exoPlayer.isInitialized) {
            playbackPosition = exoPlayer.currentPosition
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.release()
        }
    }

    private fun saveSeekPosition(videoPath: String, position: Float) {
        videoSeekCache[videoPath] = (position - 5000f)
        try {
            val json = gson.toJson(videoSeekCache)
            val file = File(filesDir, "videoSeekCache.json")
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSeekPosition(videoPath: String): Float {
        try {
            val file = File(filesDir, "videoSeekCache.json")
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<MutableMap<String, Float>>() {}.type
                val seekCache: MutableMap<String, Float> = gson.fromJson(json, type)
                return seekCache[videoPath] ?: 0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0f
    }

    private fun setOrientationBasedOnVideo() {
        val format = exoPlayer.videoFormat ?: return
        val width = format.width
        val height = format.height

        if (width == 0 || height == 0) return

        val isPortrait = height > width

        requestedOrientation = if (isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun resetBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("aspectRatioIndex", currentAspectIndex)
    }
}
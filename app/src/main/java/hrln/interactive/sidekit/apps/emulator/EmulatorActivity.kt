package hrln.interactive.sidekit.apps.emulator

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import hrln.interactive.sidekit.design.input.SystemKeys
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

class EmulatorActivity :
    Activity(),
    SurfaceHolder.Callback {
    private lateinit var surface: SurfaceView
    private lateinit var game: File
    private lateinit var library: GameLibrary
    private var handle = 0L
    private var worker: Thread? = null

    @Volatile private var running = false
    private var resumed = false
    private var ready = false
    private var paused = false
    private val keys = AtomicInteger(0)
    private val touchKeys = AtomicInteger(0)
    private val held = mutableSetOf<Int>()
    private val mapping by lazy {
        val prefs = getSharedPreferences("emulator_controls", MODE_PRIVATE)
        IntArray(10) { prefs.getInt("key_$it", defaults[it]) }
    }
    private lateinit var status: TextView
    private lateinit var pauseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        library = GameLibrary(this)
        game = library.games().firstOrNull { it.name == intent.getStringExtra("game") } ?: run {
            finish()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        status = TextView(this).apply {
            text = library.title(game)
            setTextColor(Color.WHITE)
        }
        layout.addView(status)
        surface = SurfaceView(this)
        layout.addView(surface, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this)
        pauseButton = Button(this).apply {
            text = "Pause"
            setOnClickListener {
                paused = !paused
                stop()
                if (!paused) start()
                text = if (paused) "Resume" else "Pause"
            }
        }
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(
            Button(this).apply {
                text = "Controls"
                setOnClickListener { configureControls() }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        controls.addView(
            Button(this).apply {
                text = "Exit"
                setOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        for (i in 0 until controls.childCount) {
            (controls.getChildAt(i) as Button).apply {
                textSize = 12f
                isAllCaps = false
                setSingleLine()
                setPadding(4, 4, 4, 4)
            }
        }
        layout.addView(controls)
        val shoulders = LinearLayout(this)
        listOf("L" to 9, "R" to 8).forEach { (label, bit) ->
            shoulders.addView(
                Button(this).apply {
                    text = label
                    contentDescription = "Hold $label shoulder"
                    setOnTouchListener { view, event ->
                        val mask = 1 shl bit
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> touchKeys.updateAndGet { it or mask }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                touchKeys.updateAndGet { it and mask.inv() }
                                if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                            }
                        }
                        true
                    }
                },
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        }
        layout.addView(shoulders)
        setContentView(layout)
        surface.holder.addCallback(this)
        if (!EmulatorCore.available) return fail("The emulator engine is not available in this build.")
        try {
            handle = EmulatorCore.open(game.absolutePath)
            check(handle != 0L) { "This file is not a supported Game Boy ROM." }
            val save = library.saveFile(game)
            if (save.baseFile.exists()) check(EmulatorCore.restore(handle, save.readFully())) { "The saved game could not be loaded." }
        } catch (e: Exception) {
            fail(e.message ?: "Could not open game.")
        }
    }

    private fun fail(message: String) {
        paused = true
        AlertDialog.Builder(this).setTitle("Cannot play game").setMessage(message)
            .setPositiveButton("Back") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    private fun start() {
        if (!resumed || !ready || paused || handle == 0L || worker != null) return
        running = true
        worker = Thread({
            var track: AudioTrack? = null
            try {
                val dimensions = EmulatorCore.dimensions(handle)
                val bitmap = Bitmap.createBitmap(dimensions ushr 16, dimensions and 65535, Bitmap.Config.ARGB_8888)
                val audio = ShortArray(4096)
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(16384, AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)))
                    .build().also { it.play() }
                var next = System.nanoTime()
                var lastSave = SystemClock.elapsedRealtime()
                while (running) {
                    val count = EmulatorCore.frame(handle, keys.get() or touchKeys.get(), bitmap, audio)
                    track.write(audio, 0, count, AudioTrack.WRITE_NON_BLOCKING)
                    val canvas = surface.holder.lockCanvas()
                    if (canvas != null) {
                        try {
                            canvas.drawColor(Color.BLACK)
                            val scale = minOf(canvas.width.toFloat() / bitmap.width, canvas.height.toFloat() / bitmap.height)
                            val w = (bitmap.width * scale).toInt()
                            val h = (bitmap.height * scale).toInt()
                            val x = (canvas.width - w) / 2
                            val y = (canvas.height - h) / 2
                            canvas.drawBitmap(bitmap, null, Rect(x, y, x + w, y + h), null)
                        } finally {
                            surface.holder.unlockCanvasAndPost(canvas)
                        }
                    }
                    if (SystemClock.elapsedRealtime() - lastSave > 5000) {
                        save()
                        lastSave = SystemClock.elapsedRealtime()
                    }
                    next += 16_742_706L // Both handhelds run at approximately 59.7275 Hz.
                    val delay = next - System.nanoTime()
                    if (delay > 0) {
                        LockSupport.parkNanos(delay)
                    } else if (delay < -100_000_000) {
                        next = System.nanoTime()
                    }
                }
                bitmap.recycle()
            } catch (e: Exception) {
                runOnUiThread {
                    paused = true
                    pauseButton.text = "Resume"
                    status.text = e.message ?: "Playback stopped"
                }
            } finally {
                track?.release()
                try {
                    save()
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Could not save: ${e.message}" }
                }
            }
        }, "Sidekit-mGBA").also { it.start() }
    }

    private fun save() {
        val bytes = EmulatorCore.save(handle)
        if (bytes.isEmpty()) return
        val file = library.saveFile(game)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
    private fun stop() {
        running = false
        worker?.let {
            LockSupport.unpark(it)
            it.join()
        }
        worker = null
        held.clear()
        keys.set(0)
        touchKeys.set(0)
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        start()
    }
    override fun onPause() {
        resumed = false
        stop()
        super.onPause()
    }
    override fun onDestroy() {
        stop()
        if (handle != 0L) EmulatorCore.close(handle)
        handle = 0
        super.onDestroy()
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        ready = true
        start()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ready = false
        stop()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            stop()
        } else {
            start()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (SystemKeys.isOptions(event)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) pauseButton.performClick()
            return true
        }
        if (SystemKeys.isBack(event)) return true
        val code = event.keyCode
        val mapped = code in mapping || code == KeyEvent.KEYCODE_BUTTON_START || code == KeyEvent.KEYCODE_BUTTON_SELECT
        if (mapped && !paused) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                held.add(code)
            } else if (event.action == KeyEvent.ACTION_UP) {
                held.remove(code)
            }
            var mask = 0
            mapping.forEachIndexed { index, key -> if (key in held) mask = mask or (1 shl index) }
            if (KeyEvent.KEYCODE_BUTTON_START in held) mask = mask or 8
            if (KeyEvent.KEYCODE_BUTTON_SELECT in held) mask = mask or 4
            keys.set(mask)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // The dialog listens for Back to cancel keyboard capture; it is not a predictive-back path.
    @SuppressLint("GestureBackNavigation")
    private fun configureControls() {
        paused = true
        stop()
        pauseButton.text = "Resume"
        AlertDialog.Builder(this).setTitle("Map controller buttons")
            .setItems(labels.mapIndexed { i, label -> "$label: ${KeyEvent.keyCodeToString(mapping[i]).removePrefix("KEYCODE_")}" }.toTypedArray()) { _, index ->
                val dialog = AlertDialog.Builder(this).setTitle("Press a key for ${labels[index]}")
                    .setMessage("Back cancels. Assign each action a different key.")
                    .setNegativeButton("Cancel", null).create()
                dialog.setOnKeyListener { _, code, event ->
                    if (code == KeyEvent.KEYCODE_BACK) {
                        false
                    } else {
                        if (event.action == KeyEvent.ACTION_UP) {
                            mapping.indices.filter { it != index && mapping[it] == code }.forEach { mapping[it] = KeyEvent.KEYCODE_UNKNOWN }
                            mapping[index] = code
                            getSharedPreferences("emulator_controls", MODE_PRIVATE).edit().apply {
                                mapping.forEachIndexed { i, key -> putInt("key_$i", key) }
                                apply()
                            }
                            dialog.dismiss()
                            configureControls()
                        }
                        true
                    }
                }
                dialog.show()
            }.setPositiveButton("Done", null).show()
    }
    companion object {
        // mGBA's key bit order is shared by GB and GBA.
        internal val defaults = intArrayOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1
        )
        private val labels = arrayOf("A", "B", "Select", "Start", "Right", "Left", "Up", "Down", "R", "L")
    }
}

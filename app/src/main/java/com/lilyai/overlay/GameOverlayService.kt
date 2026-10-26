package com.lilyai.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.autoplay.AutoPlayer
import com.lilyai.capture.FocusArea
import com.lilyai.capture.ScreenReader
import com.lilyai.capture.ScreenText
import com.lilyai.capture.ScreenshotManager
import com.lilyai.chess.BoardReader
import com.lilyai.chess.ChessStore
import com.lilyai.ui.Palette
import com.lilyai.ui.dp
import com.lilyai.ui.pinkButton
import kotlin.math.abs
import kotlin.math.max

// The overlay: a small pink L that sits over other apps.
// - It slides to the screen edge when idle, leaving a thin tab. Swipe the tab inward, or tap it.
// - Tapping the L opens a menu with three pages. Swipe sideways to move between them.
//   Page 1: Chat, Focus area, Teach, Auto, Pause, Stop overlay.
//   Page 2: Screenshot, Read text, Speed, Auto log.
//   Page 3: Chess: Calibrate board, Read board, which side is at the bottom.
// It runs as a foreground service so Android keeps it alive while a game is open.
class GameOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var handleView: TextView? = null
    private var menuCard: LinearLayout? = null
    private var statusLine: TextView? = null
    private var outputView: TextView? = null
    private var teachView: LinearLayout? = null
    private var selector: View? = null
    private var chat: OverlayChat? = null

    private var autoTile: Tile? = null
    private var pauseTile: Tile? = null
    private var speedTile: Tile? = null
    private var sideTile: Tile? = null

    private var menuOpen = false
    private var docked = false
    private var dockLeft = true

    private val handleWidth by lazy { dp(44) }
    private val handleHeight by lazy { dp(48) }
    private val sliver by lazy { dp(14) }
    private val menuWidth by lazy { dp(272) }
    private val dockRunnable = Runnable { dock() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AutoPlayer.stop()
        handler.removeCallbacksAndMessages(null)
        chat?.hide()
        closeFocusSelector()
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        handleView = null
        menuCard = null
        statusLine = null
        outputView = null
        teachView = null
        chat = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "LilyAi overlay", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LilyAi overlay is on")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WindowManager::class.java)

        val handle = TextView(this).apply {
            text = "L"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Palette.white)
            background = this@GameOverlayService.handleBackground()
            layoutParams = LinearLayout.LayoutParams(handleWidth, handleHeight)
        }

        val status = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Palette.plum)
        }

        // Page 1
        val chatTile = Tile(this, "\uD83D\uDCAC", "Chat") {
            closeMenu()
            openChat()
        }
        val focusTile = Tile(this, "\uD83C\uDFAF", "Focus area") {
            closeMenu()
            showFocusSelector()
        }
        val teachTile = Tile(this, "\uD83C\uDF93", "Teach") {
            closeMenu()
            teachScreen()
        }
        val auto = Tile(this, "\u25B6\uFE0F", "Auto") { toggleAuto() }
        val pause = Tile(this, "\u23F8\uFE0F", "Pause") { togglePause() }
        val stopTile = Tile(this, "\u23F9\uFE0F", "Stop overlay") { stopSelf() }

        // Page 2
        val shot = Tile(this, "\uD83D\uDCF8", "Screenshot") {
            closeMenu()
            takeScreenshot()
        }
        val read = Tile(this, "\uD83D\uDD0E", "Read text") {
            closeMenu()
            readScreen()
        }
        val speed = Tile(this, "\u23F1\uFE0F", "Speed") {
            AutoPlayer.cycleInterval(this@GameOverlayService)
            updateStates()
        }
        val logTile = Tile(this, "\uD83D\uDCDC", "Auto log") {
            closeMenu()
            showLog()
        }

        // Page 3: chess
        val calibrateTile = Tile(this, "\u265F\uFE0F", "Calibrate board") {
            closeMenu()
            calibrateChess()
        }
        val boardTile = Tile(this, "\u265E", "Read board") {
            closeMenu()
            readChess()
        }
        val side = Tile(this, "\u26AA", "White at bottom") { toggleBoardSide() }

        val pageWidth = menuWidth - dp(20)
        fun page(vararg rows: LinearLayout) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(pageWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            rows.forEach { addView(it) }
        }
        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(page(tileRow(chatTile, focusTile, teachTile), tileRow(auto, pause, stopTile)))
            addView(page(tileRow(shot, read, speed), tileRow(logTile, null, null)))
            addView(page(tileRow(calibrateTile, boardTile, side)))
        }
        val dots = TextView(this).apply {
            text = dotsFor(0)
            gravity = Gravity.CENTER
            setTextColor(Palette.pinkDeep)
        }
        val pager = PagerScroll(this).apply {
            addView(pages)
            setOnScrollChangeListener { _, scrollX, _, _, _ ->
                dots.text = dotsFor(pageAt(scrollX, pageWidth))
            }
            // When the finger lifts, settle on the nearest page.
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    val target = pageAt(scrollX, pageWidth) * pageWidth
                    post { smoothScrollTo(target, 0) }
                }
                false
            }
        }

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = this@GameOverlayService.panelBackground()
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
            addView(status)
            addView(pager, LinearLayout.LayoutParams(pageWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(dots)
        }

        // Shows the text found on screen, the Auto log, or the chess board. Tap it to hide it.
        val output = TextView(this).apply {
            visibility = View.GONE
            textSize = 12f
            maxLines = 14
            setTextColor(Palette.plum)
            background = this@GameOverlayService.panelBackground()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
            setOnClickListener {
                visibility = View.GONE
                scheduleDock()
            }
        }

        // The Teach list: tick the words Auto should tap, or stop on, for the current game.
        val teach = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = this@GameOverlayService.panelBackground()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(handle)
            addView(menu)
            addView(output)
            addView(teach)
        }

        // FLAG_LAYOUT_NO_LIMITS lets the window sit partly off the screen, which docking needs.
        params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(4)
            y = dp(160)
        }

        handle.setOnTouchListener(handleTouch(panel))
        windowManager.addView(panel, params)

        root = panel
        handleView = handle
        menuCard = menu
        statusLine = status
        outputView = output
        teachView = teach
        autoTile = auto
        pauseTile = pause
        speedTile = speed
        sideTile = side
        updateStates()
        scheduleDock()
    }

    private fun pageAt(scrollX: Int, pageWidth: Int): Int =
        ((scrollX + pageWidth / 2) / pageWidth).coerceIn(0, PAGE_COUNT - 1)

    private fun dotsFor(page: Int): String =
        (0 until PAGE_COUNT).joinToString("  ") { if (it == page) "\u25CF" else "\u25CB" }

    // ---- Moving, docking and the menu ----

    // Drag moves the L. A tap opens or closes the menu. Touching a docked L (a tap, or a swipe
    // inward) brings it back.
    private fun handleTouch(panel: View) = object : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(dockRunnable)
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > TAP_SLOP || abs(dy) > TAP_SLOP) moved = true
                    if (moved) {
                        docked = false
                        val bounds = screenBounds()
                        val expanded = menuOpen || panelBusy()
                        val minX = if (expanded) 0 else -(handleWidth - sliver)
                        val maxX = if (expanded) max(0, bounds.width() - menuWidth) else bounds.width() - sliver
                        params.x = (startX + dx).coerceIn(minX, maxX)
                        params.y = (startY + dy).coerceIn(0, bounds.height() - handleHeight)
                        windowManager.updateViewLayout(panel, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) scheduleDock() else toggleMenu()
                MotionEvent.ACTION_CANCEL -> scheduleDock()
            }
            return true
        }
    }

    // The screen's size in pixels, the same as a screenshot's.
    private fun screenBounds(): Rect = windowManager.currentWindowMetrics.bounds

    private fun panelBusy(): Boolean =
        outputView?.visibility == View.VISIBLE || teachView?.visibility == View.VISIBLE

    private fun toggleMenu() {
        if (menuOpen || panelBusy()) collapseAll() else openMenu()
    }

    private fun openMenu() {
        handler.removeCallbacks(dockRunnable)
        docked = false
        menuOpen = true
        menuCard?.visibility = View.VISIBLE
        updateStates()
        fitOnScreen()
    }

    private fun closeMenu() {
        menuOpen = false
        menuCard?.visibility = View.GONE
        scheduleDock()
    }

    private fun collapseAll() {
        menuOpen = false
        menuCard?.visibility = View.GONE
        outputView?.visibility = View.GONE
        teachView?.visibility = View.GONE
        scheduleDock()
    }

    // Keeps the wider panel (menu, text or Teach list) fully on the screen.
    private fun fitOnScreen() {
        val view = root ?: return
        docked = false
        handler.removeCallbacks(dockRunnable)
        val bounds = screenBounds()
        params.x = params.x.coerceIn(dp(8), max(dp(8), bounds.width() - menuWidth - dp(8)))
        windowManager.updateViewLayout(view, params)
    }

    private fun scheduleDock() {
        handler.removeCallbacks(dockRunnable)
        if (!menuOpen && !docked) handler.postDelayed(dockRunnable, DOCK_DELAY_MS)
    }

    // Slides the L to the nearest side, leaving only a thin tab visible.
    private fun dock() {
        if (menuOpen || docked || panelBusy()) return
        if (root == null) return
        val bounds = screenBounds()
        dockLeft = params.x + handleWidth / 2 < bounds.width() / 2
        docked = true
        animateX(if (dockLeft) -(handleWidth - sliver) else bounds.width() - sliver)
    }

    private fun animateX(target: Int) {
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 200
            addUpdateListener {
                val view = root ?: return@addUpdateListener
                params.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
    }

    // The L shows what Auto is doing: L when off, a play sign when on, a pause sign when paused.
    private fun updateStates() {
        val running = AutoPlayer.running
        val paused = AutoPlayer.paused
        autoTile?.apply {
            setActive(running)
            setLabel(if (running) "Auto on" else "Auto")
        }
        pauseTile?.apply {
            setActive(running && paused)
            setIcon(if (paused) "\u25B6\uFE0F" else "\u23F8\uFE0F")
            setLabel(if (paused) "Resume" else "Pause")
        }
        speedTile?.setLabel("Speed ${AutoPlayer.intervalSeconds(this)}s")
        val whiteAtBottom = ChessStore.whiteAtBottom(this, chessGameKey())
        sideTile?.apply {
            setIcon(if (whiteAtBottom) "\u26AA" else "\u26AB")
            setLabel(if (whiteAtBottom) "White at bottom" else "Black at bottom")
        }
        handleView?.text = when {
            running && paused -> "\u23F8\uFE0F"
            running -> "\u25B6\uFE0F"
            else -> "L"
        }
        val game = LilyAccessibilityService.foregroundGame
        val gameText = game?.let { appLabel(it) } ?: "No game yet"
        val focusText = if (currentFocus() != null) "on" else "off"
        statusLine?.text = "$gameText  |  Focus: $focusText"
    }

    // ---- Auto ----

    // Starts or stops the autoplay loop with the words saved for the current game.
    // Starting folds the menu away and docks the L, so it is out of the way of the taps.
    private fun toggleAuto() {
        if (AutoPlayer.running) {
            AutoPlayer.stop()
            toastShort("Auto stopped")
            updateStates()
            return
        }
        val targets = AutoPlayer.targets(this)
        if (targets.isEmpty()) {
            toast("No words yet. Use Teach on a game screen, or set default words in the LilyAi app")
            return
        }
        if (LilyAccessibilityService.instance == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        AutoPlayer.start(this, targets) { event ->
            toastShort(event)
            updateStates()
        }
        val stops = AutoPlayer.stopWords(this)
        val stopNote = if (stops.isEmpty()) "" else ". Stops on: " + stops.joinToString()
        val focusNote = if (currentFocus() != null) ". Only inside the focus area" else ""
        toastShort("Auto started: " + targets.joinToString() + stopNote + focusNote)
        closeMenu()
        updateStates()
        handler.removeCallbacks(dockRunnable)
        handler.postDelayed(dockRunnable, QUICK_DOCK_DELAY_MS)
    }

    private fun togglePause() {
        if (!AutoPlayer.running) {
            toastShort("Auto is not running")
            return
        }
        if (AutoPlayer.paused) AutoPlayer.resume() else AutoPlayer.pause()
        toastShort(if (AutoPlayer.paused) "Auto paused" else "Auto resumed")
        updateStates()
    }

    private fun showLog() {
        val events = AutoPlayer.recentEvents().take(LOG_LINES)
        showOutput(AutoPlayer.summary() + "\n" + events.joinToString("\n").ifEmpty { "No events yet" })
    }

    // Shows text under the L. The chess board needs a font with equal-width letters to line up.
    private fun showOutput(text: String, monospace: Boolean = false) {
        val view = outputView ?: return
        view.typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
        view.text = text
        view.visibility = View.VISIBLE
        fitOnScreen()
    }

    // ---- Chess ----

    // Chess settings are kept per game, like the focus box. Without a known game they use "default".
    private fun chessGameKey(): String = LilyAccessibilityService.foregroundGame ?: "default"

    private fun toggleBoardSide() {
        val key = chessGameKey()
        val whiteAtBottom = !ChessStore.whiteAtBottom(this, key)
        ChessStore.setWhiteAtBottom(this, key, whiteAtBottom)
        updateStates()
        toastShort(if (whiteAtBottom) "White is at the bottom" else "Black is at the bottom")
    }

    // Learns what the pieces look like. The game must be at the starting position, with the focus
    // box drawn tightly around the board.
    private fun calibrateChess() {
        val board = currentFocus()
        if (board == null) {
            toast("Draw the board with Focus area first, then try again")
            return
        }
        val key = chessGameKey()
        captureForChess { bitmap ->
            BoardReader.calibrate(bitmap, board, ChessStore.whiteAtBottom(this, key))
                .onSuccess { calibration ->
                    ChessStore.saveCalibration(this, key, calibration)
                    showOutput(
                        "Learned " + calibration.templates.size + " pieces from the starting position. " +
                            "Now try Read board."
                    )
                }
                .onFailure { showOutput("Calibration failed: ${it.message}") }
        }
    }

    // Reads the position on the board and shows it, so you can check it is right.
    private fun readChess() {
        val board = currentFocus()
        if (board == null) {
            toast("Draw the board with Focus area first, then try again")
            return
        }
        val key = chessGameKey()
        val calibration = ChessStore.loadCalibration(this, key)
        if (calibration == null) {
            toast("Calibrate first, with the game at the starting position")
            return
        }
        captureForChess { bitmap ->
            runCatching { BoardReader.read(bitmap, board, ChessStore.whiteAtBottom(this, key), calibration) }
                .onSuccess { showOutput(it.describe(), monospace = true) }
                .onFailure { showOutput("Could not read the board: ${it.message}") }
        }
    }

    // Takes a screenshot with the overlay out of the way, and hands it over. It is recycled afterwards.
    private fun captureForChess(onBitmap: (Bitmap) -> Unit) {
        val service = LilyAccessibilityService.instance
        val panel = root
        if (service == null || panel == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        panel.visibility = View.INVISIBLE
        chat?.setHidden(true)
        panel.postDelayed({
            ScreenshotManager.captureBitmap(service) { captured ->
                panel.visibility = View.VISIBLE
                chat?.setHidden(false)
                captured.onSuccess { bitmap ->
                    onBitmap(bitmap)
                    bitmap.recycle()
                }.onFailure { toast("Failed: ${it.message}") }
            }
        }, 400)
    }

    // ---- Chat ----

    private fun openChat() {
        val window = chat ?: OverlayChat(this, windowManager) { onText -> captureFocusText(onText) }
        chat = window
        window.show()
    }

    // Reads the text inside the focus area (or the whole screen if there is none) for the chat.
    private fun captureFocusText(onResult: (Result<String>) -> Unit) {
        val service = LilyAccessibilityService.instance
        val panel = root
        if (service == null || panel == null) {
            onResult(Result.failure(IllegalStateException("Turn on the LilyAi accessibility service first")))
            return
        }
        panel.visibility = View.INVISIBLE
        chat?.setHidden(true)
        panel.postDelayed({
            ScreenshotManager.captureBitmap(service) { captured ->
                panel.visibility = View.VISIBLE
                chat?.setHidden(false)
                captured.onSuccess { bitmap ->
                    ScreenReader.read(bitmap) { outcome ->
                        bitmap.recycle()
                        onResult(
                            outcome.map { lines ->
                                FocusArea.filter(lines, currentFocus())
                                    .sortedBy { it.bounds.top }
                                    .joinToString("\n") { it.text }
                                    .take(MAX_ATTACH_CHARS)
                            }
                        )
                    }
                }.onFailure { onResult(Result.failure(it)) }
            }
        }, 400)
    }

    // ---- Focus area ----

    // The focus box saved for the current game, or null if there is none.
    private fun currentFocus(): Rect? {
        val bounds = screenBounds()
        return FocusArea.get(this, LilyAccessibilityService.foregroundGame, bounds.width(), bounds.height())
    }

    private fun barButton(text: String, onClick: () -> Unit) = pinkButton(text, onClick).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(dp(3), 0, dp(3), 0) }
    }

    // Focus area: draw a box on screen. Auto, Read text, Teach, the chat and the chess board then
    // only look inside it.
    private fun showFocusSelector() {
        if (selector != null) return
        val game = LilyAccessibilityService.foregroundGame
        val label = game?.let { appLabel(it) } ?: "default"
        val bounds = screenBounds()

        val drawing = FocusSelectorView(this)
        val hint = TextView(this).apply {
            text = "Drag to draw a box around the area. Drag inside it to move it. Drag a corner to resize it."
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val save = barButton("Save") {
            val area = drawing.screenRect()
            if (area == null) {
                toastShort("Draw a box first")
            } else {
                FocusArea.save(this, game, area, bounds.width(), bounds.height())
                toastShort("Focus area saved for $label")
                closeFocusSelector()
                updateStates()
            }
        }
        val clear = barButton("Clear") {
            FocusArea.clear(this, game)
            toastShort("Focus area cleared for $label")
            closeFocusSelector()
            updateStates()
        }
        val cancel = barButton("Cancel") { closeFocusSelector() }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(save)
            addView(clear)
            addView(cancel)
        }

        val layer = FrameLayout(this).apply {
            addView(drawing, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                hint,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
                ).apply { topMargin = dp(48) }
            )
            addView(
                bar,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply { bottomMargin = dp(96) }
            )
        }

        val layerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(layer, layerParams)
        selector = layer
        currentFocus()?.let { drawing.setScreenRect(it) }
    }

    private fun closeFocusSelector() {
        selector?.let { runCatching { windowManager.removeView(it) } }
        selector = null
    }

    // ---- Screen tools ----

    private fun takeScreenshot() {
        val service = LilyAccessibilityService.instance
        val panel = root
        if (service == null || panel == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        // Hide the overlay first so it does not appear in the screenshot.
        panel.visibility = View.INVISIBLE
        chat?.setHidden(true)
        panel.postDelayed({
            ScreenshotManager.capture(service) { result ->
                panel.visibility = View.VISIBLE
                chat?.setHidden(false)
                toast(result.fold({ "Screenshot saved" }, { "Failed: ${it.message}" }))
            }
        }, 400)
    }

    // Takes a screenshot, reads the text on it and shows the lines in the overlay.
    // With a focus area, only the text inside the box is shown.
    private fun readScreen() {
        val service = LilyAccessibilityService.instance
        val panel = root
        if (service == null || panel == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        panel.visibility = View.INVISIBLE
        chat?.setHidden(true)
        panel.postDelayed({
            ScreenshotManager.captureBitmap(service) { captured ->
                panel.visibility = View.VISIBLE
                chat?.setHidden(false)
                captured.onSuccess { bitmap ->
                    ScreenReader.read(bitmap) { outcome ->
                        bitmap.recycle()
                        outcome.onSuccess { lines ->
                            val focus = currentFocus()
                            val shown = FocusArea.filter(lines, focus)
                            val where = if (focus != null) " in the focus area" else ""
                            showOutput(
                                if (shown.isEmpty()) "No text found$where" else shown.joinToString("\n") { it.text }
                            )
                        }.onFailure { toast("Failed: ${it.message}") }
                    }
                }.onFailure { toast("Failed: ${it.message}") }
            }
        }, 400)
    }

    // Teach: read the current screen and let the user tick the words Auto should tap or stop on.
    // With a focus area, only the text inside the box is listed.
    private fun teachScreen() {
        val service = LilyAccessibilityService.instance
        val panel = root
        val teach = teachView
        if (service == null || panel == null || teach == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        teach.visibility = View.GONE
        panel.visibility = View.INVISIBLE
        chat?.setHidden(true)
        panel.postDelayed({
            ScreenshotManager.captureBitmap(service) { captured ->
                panel.visibility = View.VISIBLE
                chat?.setHidden(false)
                captured.onSuccess { bitmap ->
                    ScreenReader.read(bitmap) { outcome ->
                        bitmap.recycle()
                        outcome.onSuccess { lines ->
                            val inFocus = FocusArea.filter(lines, currentFocus())
                            showTeachList(teach, candidateWords(inFocus))
                        }.onFailure { toast("Failed: ${it.message}") }
                    }
                }.onFailure { toast("Failed: ${it.message}") }
            }
        }, 400)
    }

    // Short lines are the likely buttons. Commas are removed because words are saved comma-separated.
    private fun candidateWords(lines: List<ScreenText>): List<String> =
        lines.sortedBy { it.bounds.top }
            .map { it.text.replace(",", " ").trim() }
            .filter { it.length in 2..30 }
            .distinctBy { it.lowercase() }
            .take(MAX_CHOICES)

    private fun showTeachList(teach: LinearLayout, words: List<String>) {
        val game = LilyAccessibilityService.foregroundGame
        val label = game?.let { appLabel(it) } ?: "default words"
        val saved = AutoPlayer.wordsFor(this, game).ifBlank { "none" }
        val savedStops = AutoPlayer.stopWordsFor(this, game).ifBlank { "none" }
        // The words are saved in the order they are ticked, which is their priority.
        val picked = mutableListOf<String>()

        fun line(content: String, size: Float) = TextView(this).apply {
            text = content
            textSize = size
            setTextColor(Palette.plum)
        }

        val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (words.isEmpty()) choices.addView(line("No text found on this screen", 14f))
        words.forEach { word ->
            choices.addView(
                CheckBox(this).apply {
                    text = word
                    setTextColor(Palette.plum)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) picked.add(word) else picked.remove(word)
                    }
                }
            )
        }

        // Save: Auto taps these words. Save as stop: Auto stops when it sees one of these.
        val save = barButton("Save") {
            if (picked.isEmpty()) {
                toastShort("Tick the words to tap first")
            } else {
                AutoPlayer.addWords(this, game, picked)
                toastShort("Saved for $label: " + picked.joinToString())
                teach.visibility = View.GONE
                scheduleDock()
            }
        }
        val saveStop = barButton("Save as stop") {
            if (picked.isEmpty()) {
                toastShort("Tick the stop words first")
            } else {
                AutoPlayer.addStopWords(this, game, picked)
                toastShort("Stop words for $label: " + picked.joinToString())
                teach.visibility = View.GONE
                scheduleDock()
            }
        }
        val clear = barButton("Clear all") {
            AutoPlayer.saveWordsFor(this, game, "")
            AutoPlayer.saveStopWordsFor(this, game, "")
            toastShort("Cleared the words and stop words for $label")
            teach.visibility = View.GONE
            scheduleDock()
        }
        val close = barButton("Close") {
            teach.visibility = View.GONE
            scheduleDock()
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(save)
            addView(saveStop)
        }
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(clear)
            addView(close)
        }

        teach.removeAllViews()
        teach.addView(line("Teach: $label", 14f))
        teach.addView(line("Saved: $saved", 12f))
        teach.addView(line("Stops on: $savedStops", 12f))
        teach.addView(
            ScrollView(this).apply { addView(choices) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TEACH_HEIGHT)
        )
        teach.addView(topRow)
        teach.addView(bottomRow)
        teach.visibility = View.VISIBLE
        fitOnScreen()
    }

    private fun appLabel(packageName: String): String =
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    private fun toastShort(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val CHANNEL_ID = "overlay"
        const val NOTIFICATION_ID = 1
        const val TAP_SLOP = 10
        const val PAGE_COUNT = 3
        const val TEACH_HEIGHT = 420
        const val MAX_CHOICES = 40
        const val LOG_LINES = 10
        const val MAX_ATTACH_CHARS = 2000
        const val DOCK_DELAY_MS = 3000L
        const val QUICK_DOCK_DELAY_MS = 600L
    }
}

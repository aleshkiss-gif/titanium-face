package com.titanium.face

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import java.net.Socket
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import java.io.File
import java.security.KeyStore
import java.security.KeyPairGenerator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(5, 7, 10)
    private val surface = Color.rgb(27, 33, 39)
    private val accent = Color.rgb(135, 191, 255)
    private lateinit var content: LinearLayout
    private var selectedName: String? = null
    private var selectedSize: Long = -1
    private var apkInfo = "Не проверено"
    private var selectedUri: Uri? = null
    private var currentTab = 1
    private var watchAddress = ""
    private var pairingPort = ""
    private var connectionPort = ""
    private var adbManager: WatchAdbManager? = null
    private var adbConnected = false
    private var connectionStatusView: TextView? = null
    private var connectionStatusNote: TextView? = null
    private var connectionStatusPanel: LinearLayout? = null
    private var pairingCodeDraft = ""
    private var inventoryRefresh: (() -> Unit)? = null
    private val watchOperation = java.util.concurrent.atomic.AtomicBoolean(false)
    private val prefs by lazy { getSharedPreferences("watch_connection", MODE_PRIVATE) }
    private val managedPrefs by lazy { getSharedPreferences("managed_watch_packages", MODE_PRIVATE) }
    private fun rememberInstalled(pkg: String, name: String) {
        if (pkg.matches(Regex("[a-zA-Z_][a-zA-Z0-9_.]*"))) managedPrefs.edit().putString(pkg, name).apply()
    }
    private fun glass(radius: Int = 24): android.graphics.drawable.Drawable =
        object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: android.graphics.Canvas) {
                val b = bounds
                val r = dp(radius).toFloat()
                val rect = android.graphics.RectF(b)
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, b.width().toFloat(), b.height().toFloat(),
                    intArrayOf(0xff29323B.toInt(), 0xff171E26.toInt(), 0xff11171E.toInt()),
                    null, android.graphics.Shader.TileMode.CLAMP
                )
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawRoundRect(rect, r, r, paint)
                paint.shader = null
                paint.color = 0x44D8F3FF
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = dp(1).toFloat()
                canvas.drawRoundRect(android.graphics.RectF(b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f), r, r, paint)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = 0x28D9F1FF
                val step = dp(12).coerceAtLeast(8)
                var y = b.top + step / 2
                while (y < b.bottom - step / 2) {
                    var x = b.left + step / 2
                    while (x < b.right - step / 2) {
                        val dx = x - b.left
                        val dy = y - b.top
                        val corner = dp(radius)
                        if ((dx > corner && dx < b.width() - corner) ||
                            (dy > corner && dy < b.height() - corner)) {
                            canvas.drawCircle(x.toFloat(), y.toFloat(), dp(0).toFloat() + 0.7f, paint)
                        }
                        x += step
                    }
                    y += step
                }
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun dp(n: Float) = (n * resources.displayMetrics.density)
    private fun rounded(color: Int, radius: Int = 22) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xff343F45.toInt(), color, 0xff141B21.toInt())
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0x44A4C7CA)
    }
    private fun jelly(view: View) {
        view.isClickable = true
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(0.92f).scaleY(0.89f).setDuration(115).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(520)
                        .setInterpolator(android.view.animation.OvershootInterpolator(3.0f)).start()
                }
            }
            false
        }
    }
    private var backdropAnimator: android.animation.ValueAnimator? = null
    private fun dialBackdrop(): android.graphics.drawable.Drawable =
        object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            
            override fun draw(canvas: android.graphics.Canvas) {
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                if (w <= 0f || h <= 0f) return
                canvas.drawColor(0xff050A13.toInt())
                paint.style = android.graphics.Paint.Style.FILL
                paint.shader = android.graphics.RadialGradient(
                    w * 0.5f, h * 0.38f, w * 0.95f,
                    intArrayOf(0xff102947.toInt(), 0xff091727.toInt(), 0xff050A13.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                // Deep blue dotted field: gently drifting waves of light.
                val spacing = dp(23).toFloat()
                val columns = (w / spacing).toInt() + 2
                val rows = (h / spacing).toInt() + 2
                for (iy in 0..rows) {
                    for (ix in 0..columns) {
                        val x = ix * spacing + (iy % 2) * spacing * 0.5f
                        val y = iy * spacing
                        val wave = kotlin.math.sin(ix * 0.43f + iy * 0.29f - backdropPhase).toFloat()
                        val drift = kotlin.math.sin(iy * 0.32f + backdropPhase * 0.7f).toFloat() * dp(2)
                        val glow = ((wave + 1f) * 0.5f)
                        val alpha = (18 + glow * 68).toInt().coerceIn(0, 255)
                        paint.color = Color.argb(alpha, 70, 162, 255)
                        canvas.drawCircle(x + drift, y, dp(0.9f) + glow * dp(0.7f), paint)
                    }
                }

                // Large translucent WFM lettering behind the controls.
                paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.textSize = w * 0.29f
                val cx = w * 0.5f
                val cy = h * 0.41f
                val baseline = cy - (paint.ascent() + paint.descent()) / 2f
                for (layer in 9 downTo 1) {
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = dp(layer * 2).toFloat()
                    paint.color = Color.argb((2 + layer / 3), 40, 130, 245)
                    canvas.drawText("WFM", cx, baseline, paint)
                }
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = Color.argb(17, 86, 168, 255)
                canvas.drawText("WFM", cx, baseline, paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
        }
    private fun startBackdropAnimation(drawable: android.graphics.drawable.Drawable) {
        backdropAnimator?.cancel()
        val animated = drawable
        backdropAnimator = android.animation.ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 11000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                // The drawable owns the animation phase and invalidates the background.
                backdropPhase = it.animatedValue as Float
                animated.invalidateSelf()
            }
            start()
        }
    }
    private var backdropPhase = 0f
    private fun label(s: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun gap(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun card(title: String, subtitle: String, action: (() -> Unit)? = null) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = glass()
        }
        panel.addView(label(title, 19f, true))
        panel.addView(gap(9))
        panel.addView(label(subtitle, 14f, color = 0xffB8C0D0.toInt()))
        if (action != null) { panel.isClickable = true; panel.background = rounded(0xff2478C9.toInt()); jelly(panel); panel.setOnClickListener { action() } }
        content.addView(panel, LinearLayout.LayoutParams(-1, -2))
        content.addView(gap(14))
    }
    private fun actionCard(title: String, subtitle: String, onClick: () -> Unit): TextView {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = glass()
            isClickable = true
            isFocusable = true
        }
        val heading = label(title, 19f, true)
        panel.addView(heading)
        panel.addView(gap(9))
        panel.addView(label(subtitle, 14f, color = 0xffB8C0D0.toInt()))
        jelly(panel)
        panel.setOnClickListener { onClick() }
        content.addView(panel, LinearLayout.LayoutParams(-1, -2))
        content.addView(gap(14))
        return heading
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchAddress = prefs.getString("watch_ip", "") ?: ""
        connectionPort = prefs.getString("adb_port", "") ?: ""
        pairingPort = prefs.getString("pairing_port", "") ?: ""
        show(1)
        autoReconnect()
    }
    private var lastAutoConnectAt = 0L
    private var connectionMessage = "○ ADB не подключено"

    private fun autoReconnect() {
        if (prefs.getString("watch_ip", "").isNullOrBlank()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAutoConnectAt < 12000L) return
        if (!watchOperation.compareAndSet(false, true)) return
        lastAutoConnectAt = now
        connectionMessage = "Подключаемся к часам…"
        runOnUiThread { updateConnectionStatus() }
        thread {
            val outcome = try {
                ensureWatchConnected()
                "● Часы подключены"
            } catch (e: Exception) {
                "○ Не удалось подключиться автоматически. Проверь Wi-Fi и порт ADB."
            }
            runOnUiThread {
                connectionMessage = outcome
                watchOperation.set(false)
                updateConnectionStatus()
                if (currentTab == 2) show(2)
            }
        }
    }

    override fun onDestroy() { backdropAnimator?.cancel(); super.onDestroy() }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) autoReconnect()
    }

    private fun ensureWatchConnected(): WatchAdbManager {
        val host = prefs.getString("watch_ip", watchAddress)?.trim().orEmpty()
        var port = prefs.getString("adb_port", connectionPort)?.toIntOrNull()
        require(host.isNotBlank()) { "Укажи IP часов на вкладке «Часы»" }
        val existing = adbManager
        if (existing?.isConnected == true) {
            try {
                if (WatchApkInstaller.probe(existing)) return existing
            } catch (_: Exception) { }
            adbManager = null
            adbConnected = false
        }
        // ADB TLS ports change across wireless-debugging sessions.
        // Missing or stale saved ports must not prevent discovery.
        val savedPort = port?.takeIf { it in 1..65535 }

        var lastError: Exception? = null
        fun tryPort(attemptPort: Int): WatchAdbManager? {
            repeat(2) { attempt ->
                try {
                    val candidate = WatchAdbManager(applicationContext)
                    if (candidate.connect(host, attemptPort) && candidate.isConnected &&
                        WatchApkInstaller.probe(candidate)) return candidate
                } catch (e: Exception) { lastError = e }
                if (attempt == 0) Thread.sleep(350L)
            }
            return null
        }
        var connectedPort = savedPort ?: 0
        var connectedManager = savedPort?.let { tryPort(it) }
        if (connectedManager == null) {
            val discovered = try { WatchDiscovery.find(applicationContext, host, 6500L) }
                catch (_: Exception) { null }
            if (discovered?.host == host && discovered.port in 1..65535) {
                connectedManager = tryPort(discovered.port)
                if (connectedManager != null) connectedPort = discovered.port
            }
        }
        if (connectedManager != null) {
            adbManager = connectedManager
            adbConnected = true
            connectionPort = connectedPort.toString()
            prefs.edit().putString("adb_port", connectionPort).apply()
            runOnUiThread { updateConnectionStatus() }
            return connectedManager
        }
        adbManager = null
        adbConnected = false
        runOnUiThread { updateConnectionStatus() }
        throw IllegalStateException(
            "Не удалось подключиться к $host. Сохранённый порт: ${savedPort ?: "не указан"}. Автопоиск ADB не нашёл доступное соединение. Проверь Wi-Fi и беспроводную отладку.",
            lastError
        )
    }

    private fun updateConnectionStatus() {
        val connected = adbConnected && adbManager?.isConnected == true
        val configured = prefs.getString("watch_ip", "").orEmpty().isNotBlank()
        val panelColor = when {
            connected -> 0xff237849.toInt()
            configured -> 0xff225A96.toInt()
            else -> 0xff424A54.toInt()
        }
        connectionStatusPanel?.background = rounded(panelColor)
        connectionStatusView?.text = when {
            connected -> "● Часы подключены"
            configured -> "◌ Часы в режиме ожидания"
            else -> "○ Часы отключены"
        }
        connectionStatusView?.setTextColor(Color.WHITE)
        connectionStatusNote?.text = when {
            connected -> "Galaxy Watch Ultra · соединение активно"
            configured -> "Автоматически подключимся после пробуждения"
            else -> "Открой настройки и подключи часы"
        }
    }

    private fun show(tab: Int) {
        currentTab = tab
        inventoryRefresh = null
        connectionStatusView = null
        connectionStatusNote = null
        connectionStatusPanel = null
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = dialBackdrop()
        }
        startBackdropAnimation(frame.background)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(30), dp(22), dp(20)) }
        scroll.addView(content)
        frame.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(label("WatchFace Manager", 27f, true))
        content.addView(gap(7))
        content.addView(label(if (tab == 2) "Первое сопряжение с часами" else "Циферблаты и установка", 15f, color = accent))
        content.addView(gap(16))
        if (tab == 2) {
            val back = TextView(this).apply {
                text = "‹  Вернуться на главную"
                textSize = 16f
                setTextColor(Color.WHITE)
                background = rounded(0xff2478C9.toInt())
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener { show(1) }
            }
            content.addView(back)
            content.addView(gap(12))
        }
        if (tab == 1) {
            val logo = TextView(this).apply {
                text = "WFM"
                gravity = Gravity.CENTER
                textSize = 44f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xff071526.toInt())
                    setStroke(dp(4), 0xff39AEFF.toInt())
                }
                elevation = dp(12).toFloat()
                contentDescription = "WFM · подключить часы"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val host = prefs.getString("watch_ip", "").orEmpty()
                    if (host.isBlank()) {
                        show(2)
                    } else if (adbConnected && adbManager?.isConnected == true) {
                        android.widget.Toast.makeText(this@MainActivity, "Часы уже подключены", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        autoReconnect()
                    }
                }
            }
            val logoHolder = FrameLayout(this)
            logoHolder.addView(logo, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER))
            content.addView(logoHolder, LinearLayout.LayoutParams(-1, dp(230)))
            content.addView(gap(12))
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = glass(20)
                isClickable = true
                isFocusable = true
                setOnClickListener { logo.performClick() }
            }
            panel.addView(label("СОЕДИНЕНИЕ С ЧАСАМИ", 12f, true, color = Color.WHITE))
            panel.addView(gap(7))
            connectionStatusView = label("◌ Часы в режиме ожидания", 16f, true)
            panel.addView(connectionStatusView)
            panel.addView(gap(5))
            connectionStatusNote = label("Нажми WFM для подключения", 12f, color = Color.WHITE)
            panel.addView(connectionStatusNote)
            connectionStatusPanel = panel
            content.addView(panel)
            updateConnectionStatus()
            content.addView(gap(20))
        } else content.addView(gap(14))
        when(tab) {
            1 -> {
                if (selectedUri != null) {
                    val mb = if (selectedSize >= 0) String.format("%.2f МБ", selectedSize / 1048576.0) else "Размер неизвестен"
                    card("✓  Файл выбран · Изменить", (selectedName ?: "APK / ZIP") + "\n" + mb + "\n" + apkInfo + "\nНажми для выбора другого файла.") { pickApk() }
                } else {
                    card("＋  Выбрать APK или ZIP", "Нажми, чтобы выбрать файл циферблата.") { pickApk() }
                }
                val installStatus = label("Установка ещё не запускалась", 14f, color = accent)
                val progressFrame = FrameLayout(this).apply {
                    background = rounded(0xff2478C9.toInt())
                    clipToOutline = true
                }
                val progressFill = View(this).apply { setBackgroundColor(0x665BFF9B) }
                progressFrame.addView(progressFill, FrameLayout.LayoutParams(0, -1, Gravity.START))
                val progressLabel = label("↓  Установить циферблат", 19f, true).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(20), dp(20), dp(20))
                }
                progressFrame.addView(progressLabel, FrameLayout.LayoutParams(-1, -1))
                var displayedProgress = 0
                fun setProgress(percent: Int, title: String) {
                    val target = percent.coerceIn(displayedProgress, 100)
                    val old = displayedProgress
                    displayedProgress = target
                    progressLabel.text = title
                    progressFrame.post {
                        val from = (progressFrame.width * old / 100f).toInt()
                        val to = (progressFrame.width * target / 100f).toInt()
                        android.animation.ValueAnimator.ofInt(from, to).apply {
                            duration = 250L
                            addUpdateListener { animation ->
                                progressFill.layoutParams = (progressFill.layoutParams as FrameLayout.LayoutParams).apply {
                                    width = animation.animatedValue as Int
                                }
                            }
                            start()
                        }
                    }
                }
                val installButton = Button(this).apply {
                    text = "Установить выбранный APK на часы"; isAllCaps = false
                    setTextColor(Color.WHITE)
                    textSize = 19f
                    setTypeface(null, Typeface.BOLD)
                    background = rounded(0xff2478C9.toInt())
                    setOnClickListener {
                        val uri = selectedUri
                        if (uri == null) {
                            installStatus.text = "Сначала выбери APK циферблата"
                        } else {
                            if (!watchOperation.compareAndSet(false, true)) return@setOnClickListener
                            isEnabled = false
                            progressFrame.isEnabled = false
                            displayedProgress = 0
                            setProgress(3, "Подготовка файла · 3%")
                            installStatus.text = "Подготовка APK…"
                            thread {
                                val message = try {
                                    val apk = ApkArchiveReader.extract(this@MainActivity, uri, selectedName)
                                    runOnUiThread { setProgress(15, "Распаковка завершена · 15%") }
                                    var response: String? = null
                                    var lastError: Exception? = null
                                    for (attempt in 1..3) {
                                        try {
                                            runOnUiThread { installStatus.text = if (attempt == 1) "Устанавливаем…" else "Восстанавливаем ADB: попытка $attempt из 3…" }
                                            val connectedManager = ensureWatchConnected()
                                            runOnUiThread { setProgress(20, "Подключено · 20%") }
                                            response = WatchApkInstaller.install(connectedManager, apk) { percent ->
                                                runOnUiThread { val value = if (percent > 100) 90 else 20 + percent * 65 / 100; setProgress(value, if (percent > 100) "Установка на часах · 90%" else "Передача · $value%"); installStatus.text = if (percent > 100) "Устанавливаем APK на часах…" else "Передача на часы: $percent%" }
                                            }
                                            runOnUiThread { setProgress(95, "Проверка установки · 95%") }
                                            break
                                        } catch (e: Exception) {
                                            lastError = e
                                            val transient = e.message.orEmpty().contains("Connection", true) ||
                                                e is java.io.IOException || e.message.orEmpty().contains("closed", true)
                                            if (!transient || attempt == 3) break
                                            adbManager = null
                                            Thread.sleep(1000L * attempt)
                                        }
                                    }
                                    if (response == null) throw lastError ?: IllegalStateException("ADB установка не удалась")
                                    @Suppress("DEPRECATION")
                                    val installedInfo = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
                                    if (installedInfo != null) rememberInstalled(installedInfo.packageName, installedInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: installedInfo.packageName)
                                    apk.delete()
                                    "Установка завершена: $response"
                                } catch (e: Exception) {
                                    android.util.Log.e("TitaniumApkInstall", "Install failed", e)
                                    "Ошибка установки: " + (e.localizedMessage ?: e.javaClass.simpleName)
                                }
                                runOnUiThread { if (message.startsWith("Установка завершена")) setProgress(100, "✓  Циферблат установлен") else progressLabel.text = "↓  Повторить установку"; installStatus.text = message; isEnabled = true; progressFrame.isEnabled = true; watchOperation.set(false); inventoryRefresh?.invoke() }
                            }
                        }
                    }
                }
                jelly(progressFrame)
                progressFrame.setOnClickListener { if (progressFrame.isEnabled) { installButton.performClick() } }
                content.addView(progressFrame, LinearLayout.LayoutParams(-1, dp(88)))
                content.addView(gap(8))
                content.addView(installStatus)
                content.addView(gap(24))
                content.addView(label("Установленные", 21f, true))
                content.addView(gap(8))
                val inventoryStatus = label("Список установленных циферблатов", 14f, color = accent)
                val inventoryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                fun refreshInventory() {
                    if (!watchOperation.compareAndSet(false, true)) return
                    inventoryStatus.text = "Подключаемся и проверяем установленные пакеты…"
                    inventoryList.removeAllViews()
                    thread {
                        try {
                            val installed = WatchApkInstaller.installedPackages(ensureWatchConnected())
                            val managed = managedPrefs.all.filter { it.key in installed && it.value is String }
                            runOnUiThread {
                                watchOperation.set(false)
                                inventoryStatus.text = if (managed.isEmpty()) "Установленные циферблаты пока не найдены." else "Найдено: ${managed.size}"
                                for ((pkg, displayName) in managed) {
                                    val row = LinearLayout(this@MainActivity).apply {
                                        orientation = LinearLayout.VERTICAL
                                        setPadding(dp(18), dp(16), dp(18), dp(16))
                                        background = rounded(surface)
                                    }
                                    row.addView(label(displayName.toString(), 18f, true))
                                    row.addView(label(pkg, 12f, color = 0xffB8C0D0.toInt()))
                                    val removeFrame = FrameLayout(this@MainActivity).apply {
                                        background = rounded(0xff2478C9.toInt())
                                        clipToOutline = true
                                    }
                                    val removeFill = View(this@MainActivity).apply { setBackgroundColor(0x993A94FF.toInt()) }
                                    removeFrame.addView(removeFill, FrameLayout.LayoutParams(0, -1, Gravity.START))
                                    val removeLabel = label("Удалить циферблат", 16f, true).apply {
                                        gravity = Gravity.CENTER
                                    }
                                    removeFrame.addView(removeLabel, FrameLayout.LayoutParams(-1, -1))
                                    var removing = false
                                    fun advanceRemoval(percent: Int, title: String) {
                                        removeLabel.text = title
                                        removeFrame.post {
                                            val target = removeFrame.width * percent.coerceIn(0, 100) / 100
                                            val previous = removeFill.width
                                            android.animation.ValueAnimator.ofInt(previous, target).apply {
                                                duration = 320L
                                                addUpdateListener { animation ->
                                                    removeFill.layoutParams = (removeFill.layoutParams as FrameLayout.LayoutParams).apply {
                                                        width = animation.animatedValue as Int
                                                    }
                                                }
                                                start()
                                            }
                                        }
                                    }
                                    jelly(removeFrame)
                                    removeFrame.setOnClickListener {
                                        if (removing || watchOperation.get()) return@setOnClickListener
                                        android.app.AlertDialog.Builder(this@MainActivity)
                                            .setTitle("Удалить циферблат?")
                                            .setMessage("Удалить ${displayName} (${pkg}) с часов?")
                                            .setNegativeButton("Отмена", null)
                                            .setPositiveButton("Удалить") { _, _ ->
                                                if (!watchOperation.compareAndSet(false, true)) return@setPositiveButton
                                                removing = true
                                                advanceRemoval(12, "Подключение · 12%")
                                                inventoryStatus.text = "Удаляем ${displayName}…"
                                                thread {
                                                    val result = try {
                                                        val manager = ensureWatchConnected()
                                                        runOnUiThread { advanceRemoval(35, "Удаление на часах…") }
                                                        val output = WatchApkInstaller.uninstallPackage(manager, pkg)
                                                        managedPrefs.edit().remove(pkg).apply()
                                                        "Удалено: ${pkg} (${output})"
                                                    } catch (e: Exception) {
                                                        "Ошибка удаления: " + (e.localizedMessage ?: e.javaClass.simpleName)
                                                    }
                                                    runOnUiThread {
                                                        if (result.startsWith("Удалено:")) {
                                                            advanceRemoval(100, "✓ Удалено")
                                                        } else {
                                                            advanceRemoval(0, "Повторить удаление")
                                                        }
                                                        inventoryStatus.text = result
                                                        removing = false
                                                        watchOperation.set(false)
                                                        if (result.startsWith("Удалено:")) {
                                                            removeFrame.postDelayed({ if (currentTab == 1) refreshInventory() }, 450)
                                                        }
                                                    }
                                                }
                                            }.show()
                                    }
                                    row.addView(gap(12))
                                    row.addView(removeFrame, LinearLayout.LayoutParams(-1, dp(60)))
                                    inventoryList.addView(row)
                                    inventoryList.addView(gap(12))
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { watchOperation.set(false); inventoryStatus.text = "Не удалось проверить часы: " + (e.localizedMessage ?: "нет соединения") }
                        }
                    }
                }
                inventoryRefresh = { refreshInventory() }
                content.addView(inventoryStatus)
                content.addView(gap(12))
                content.addView(inventoryList)
                // Refresh only with an existing live ADB connection; never compete with a new connection.
                // Inventory is refreshed after successful install or uninstall, not on tab entry.

            }
            else -> {

                pairingForm()

            }
        }
        content.addView(gap(12))
        content.addView(label("Версия 0.42 • Emerald Glass", 12f, color = 0xff8F99AA.toInt()).apply { gravity = Gravity.CENTER })
        setContentView(frame)
    }
    private fun pairingForm() {
        content.addView(label("Подключение к часам", 22f, true))
        content.addView(gap(12))
        fun field(hintText: String, value: String, numeric: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                setText(value)
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(0xff9FA9B9.toInt())
                inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            }
        content.addView(label("Автоматический поиск по Wi-Fi. Для установки включи беспроводную отладку на часах.", 14f, color = 0xffB8C9BE.toInt()))
        content.addView(gap(14))
        fun groupedFields(title: String, description: String, left: EditText, right: EditText) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = glass(22)
            }
            panel.addView(label(title, 17f, true))
            panel.addView(gap(5))
            panel.addView(label(description, 12f, color = 0xffA5B9AE.toInt()))
            panel.addView(gap(12))
            val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fields.addView(left, LinearLayout.LayoutParams(0, dp(54), 1f))
            fields.addView(Space(this), LinearLayout.LayoutParams(dp(10), 1))
            fields.addView(right, LinearLayout.LayoutParams(0, dp(54), 1f))
            panel.addView(fields)
            content.addView(panel, LinearLayout.LayoutParams(-1, -2))
            content.addView(gap(14))
        }
        val ip = field("IP часов", watchAddress)
        val adbPort = field("Порт ADB", connectionPort, true)
        groupedFields("Подключение", "Определяются автоматически · можно ввести вручную", ip, adbPort)
        val pairPort = field("Порт сопряжения", pairingPort, true)
        val pin = field("Код из 6 цифр", "", true)
        pin.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        groupedFields("Первое сопряжение", "Заполняется только при первом подключении", pairPort, pin)
        content.addView(gap(18))
        val status = label(connectionMessage, 15f, color = accent)
        connectionStatusView = status
        val button = TextView(this).apply {
            text = "Подключиться к часам"
            textSize = 19f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(0xff2478C9.toInt())
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        fun updateButton() {
            val connected = adbConnected && adbManager?.isConnected == true
            button.text = if (connected) "✓ Подключено к часам" else "Найти и подключить часы"
            button.background = rounded(if (connected) 0xff278B57.toInt() else 0xff2478C9.toInt())
            status.text = if (connected) "● ADB подключено" else "○ ADB не подключено"
        }
        updateButton()
        button.setOnClickListener {
            if (!watchOperation.compareAndSet(false, true)) return@setOnClickListener
            val manualHost = ip.text.toString().trim()
            val manualPort = adbPort.text.toString().trim()
            val temporaryPort = pairPort.text.toString().trim()
            val code = pin.text.toString().trim()
            status.text = "Ищем часы в локальной сети…"
            button.isEnabled = false
            thread {
                val result = try {
                    val discovered = if (manualHost.isBlank() || manualPort.isBlank()) WatchDiscovery.find(applicationContext, manualHost.ifBlank { null }) else null
                    val host = manualHost.ifBlank { discovered?.host.orEmpty() }
                    val port = manualPort.ifBlank { discovered?.port?.toString().orEmpty() }
                    require(host.isNotBlank() && (port.toIntOrNull()?.let { it in 1..65535 } == true)) {
                        "Часы не найдены. Включи Wi-Fi и беспроводную отладку или введи IP и порт вручную."
                    }
                    watchAddress = host
                    connectionPort = port
                    pairingPort = temporaryPort
                    prefs.edit().putString("watch_ip", host).putString("adb_port", port)
                        .putString("pairing_port", temporaryPort).apply()
                    if (code.isNotEmpty()) {
                        require(code.matches(Regex("[0-9]{6}"))) { "Код должен содержать 6 цифр" }
                        val pp = temporaryPort.toIntOrNull()
                        require(pp != null && pp in 1..65535) { "Укажи порт сопряжения" }
                        // Pairing and TLS connection use different ports and sessions.
                        // Do not reuse a manager left in a failed TLS state.
                        val pairingManager = WatchAdbManager(applicationContext)
                        val paired = try { pairingManager.pair(host, pp, code) } catch (e: Exception) {
                            throw IllegalStateException("Сопряжение не завершено: " + (e.localizedMessage ?: "соединение сброшено") +
                                ". Открой новый код сопряжения на часах и введи новый порт.", e)
                        }
                        require(paired) { "Часы отклонили сопряжение. Открой новый код и порт сопряжения." }
                        adbManager = pairingManager
                    }
                    ensureWatchConnected()
                    "Соединение установлено: $host:$port"
                } catch (e: Exception) {
                    val detail = e.localizedMessage ?: "Ошибка подключения"
                    if (detail.contains("Connection reset", ignoreCase = true))
                        "Часы сбросили соединение. Проверь актуальные порты ADB и сопряжения; для нового сопряжения открой свежий код на часах."
                    else detail
                }
                runOnUiThread {
                    pin.text.clear()
                    ip.setText(watchAddress)
                    adbPort.setText(connectionPort)
                    status.text = result
                    button.isEnabled = true
                    watchOperation.set(false)
                    updateButton()
                    if (adbManager?.isConnected != true) status.text = result
                }
            }
        }
        jelly(button)
        content.addView(button, LinearLayout.LayoutParams(-1, dp(74)))
        content.addView(gap(12))
        content.addView(status)
    }
    private fun pickApk() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }, 10)
    }
    @Deprecated("Legacy activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
        selectedUri = uri
        selectedName = null
        selectedSize = -1
        apkInfo = "Чтение APK…"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) selectedName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) selectedSize = cursor.getLong(sizeIndex)
            }
        }
        if (selectedName == null) selectedName = uri.lastPathSegment ?: "APK"
        show(1)
        thread {
            val description = try {
                val tmp = ApkArchiveReader.extract(this@MainActivity, uri, selectedName)
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageArchiveInfo(tmp.absolutePath, 0)
                    ?: throw IllegalArgumentException("Файл не распознан как APK")
                tmp.delete()
                "Пакет: " + info.packageName + "\nВерсия: " + (info.versionName ?: "не указана")
            } catch (e: Exception) {
                "Ошибка чтения APK: " + (e.localizedMessage ?: "неизвестная ошибка")
            }
            runOnUiThread { apkInfo = description; if (currentTab == 1) show(1) }
        }
    }
}

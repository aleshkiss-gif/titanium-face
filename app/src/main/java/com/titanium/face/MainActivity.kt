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
    private val backgroundColor = Color.rgb(11, 13, 19)
    private val surface = Color.rgb(28, 32, 42)
    private val accent = Color.rgb(114, 177, 255)
    private lateinit var content: LinearLayout
    private var selectedName: String? = null
    private var selectedSize: Long = -1
    private var apkInfo = "Не проверено"
    private var selectedUri: Uri? = null
    private var currentTab = 0
    private var watchAddress = ""
    private var pairingPort = ""
    private var connectionPort = ""
    private var adbManager: WatchAdbManager? = null
    private var adbConnected = false
    private var connectionStatusView: TextView? = null
    private var connectionStatusNote: TextView? = null
    private var pairingCodeDraft = ""
    private val prefs by lazy { getSharedPreferences("watch_connection", MODE_PRIVATE) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 22) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(s: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun gap(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun card(title: String, subtitle: String, action: (() -> Unit)? = null) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = rounded(surface)
        }
        panel.addView(label(title, 19f, true))
        panel.addView(gap(9))
        panel.addView(label(subtitle, 14f, color = 0xffB8C0D0.toInt()))
        if (action != null) { panel.isClickable = true; panel.setOnClickListener { action() } }
        content.addView(panel, LinearLayout.LayoutParams(-1, -2))
        content.addView(gap(14))
    }
    private fun actionCard(title: String, subtitle: String, onClick: () -> Unit): TextView {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(surface)
            isClickable = true
            isFocusable = true
        }
        val heading = label(title, 19f, true)
        panel.addView(heading)
        panel.addView(gap(9))
        panel.addView(label(subtitle, 14f, color = 0xffB8C0D0.toInt()))
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
        show(0)
    }
    private fun ensureWatchConnected(): WatchAdbManager {
        val manager = adbManager ?: WatchAdbManager(applicationContext).also { adbManager = it }
        if (manager.isConnected) return manager
        val host = prefs.getString("watch_ip", watchAddress)?.trim().orEmpty()
        val port = prefs.getString("adb_port", connectionPort)?.toIntOrNull()
        require(host.isNotEmpty() && port != null && port in 1..65535) {
            "Сначала укажи IP и порт подключения на вкладке «Часы»"
        }
        var connected = false
        repeat(3) { attempt ->
            if (!connected) {
                connected = try { manager.connect(host, port) } catch (_: Exception) { false }
                if (!connected && attempt < 2) Thread.sleep(900L * (attempt + 1))
            }
        }
        require(connected) {
            "Не удалось восстановить ADB. Проверь беспроводную отладку и текущий порт часов"
        }
        adbConnected = true
        runOnUiThread { updateConnectionStatus() }
        return manager
    }

    private fun updateConnectionStatus() {
        val connected = adbManager?.isConnected == true
        connectionStatusView?.text = if (connected) "● ADB подключено" else "○ ADB не подключено"
        connectionStatusNote?.text = if (connected) "Galaxy Watch Ultra · соединение активно" else "Galaxy Watch Ultra · нажми «Проверить подключение»"
    }

    private fun show(tab: Int) {
        currentTab = tab
        connectionStatusView = null
        connectionStatusNote = null
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(30), dp(22), dp(20)) }
        scroll.addView(content)
        frame.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(label("TITANIUM FACE", 29f, true))
        content.addView(gap(7))
        content.addView(label(when(tab) { 0 -> "Коллекция циферблатов"; 1 -> "Файлы и установка"; else -> "Подключение Galaxy Watch" }, 15f, color = accent))
        content.addView(gap(30))
        when(tab) {
            0 -> {
                card("◉  TITANIUM ULTRA", "Фирменный циферблат. Скоро здесь появятся превью и загрузка APK.")
                card("✦  Новые коллекции", "Спортивные, классические и минималистичные циферблаты.")
                card("♡  Избранное", "В будущей версии здесь будут сохранённые циферблаты.")
            }
            1 -> {
                card("＋  Выбрать APK или ZIP", "Выбрать APK или ZIP-архив с одним APK.") { pickApk() }
                if (selectedUri != null) {
                    val mb = if (selectedSize >= 0) String.format("%.2f МБ", selectedSize / 1048576.0) else "Размер неизвестен"
                    card("✓  Файл выбран", (selectedName ?: "APK / ZIP") + "\n" + mb + "\n" + apkInfo + "\n\nСовместимость с Wear OS пока не подтверждена.")
                } else card("Нет выбранного файла", "Выбери APK или ZIP для просмотра информации.")
                card("Установка на часы", "Выбери APK или ZIP с одним APK и подключись к часам по ADB TLS. Архив распакуется автоматически.")
                val installStatus = label("Установка ещё не запускалась", 14f, color = accent)
                val installButton = Button(this).apply {
                    text = "Установить выбранный APK на часы"; isAllCaps = false
                    setOnClickListener {
                        val uri = selectedUri
                        if (uri == null) {
                            installStatus.text = "Сначала выбери APK циферблата"
                        } else {
                            isEnabled = false
                            installStatus.text = "Подготовка APK…"
                            thread {
                                val message = try {
                                    val apk = ApkArchiveReader.extract(this@MainActivity, uri, selectedName)
                                    var response: String? = null
                                    var lastError: Exception? = null
                                    for (attempt in 1..3) {
                                        try {
                                            runOnUiThread { installStatus.text = if (attempt == 1) "Устанавливаем…" else "Восстанавливаем ADB: попытка $attempt из 3…" }
                                            response = WatchApkInstaller.install(ensureWatchConnected(), apk) { percent ->
                                                runOnUiThread { installStatus.text = "Передача на часы: $percent%" }
                                            }
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
                                    apk.delete()
                                    "Установка завершена: $response"
                                } catch (e: Exception) {
                                    android.util.Log.e("TitaniumApkInstall", "Install failed", e)
                                    "Ошибка установки: " + (e.localizedMessage ?: e.javaClass.simpleName)
                                }
                                runOnUiThread { installStatus.text = message; isEnabled = true }
                            }
                        }
                    }
                }
                actionCard("↓  Установить APK на часы", "Передать выбранный APK или ZIP на Galaxy Watch", { installButton.performClick() })
                content.addView(gap(8))
                content.addView(installStatus)
                content.addView(gap(24))
                card("Удаление старого циферблата", "Удаляет только TITANIUM ULTRA 1.0 (пакет com.titanium.ultra) с часов. Другие циферблаты не затрагиваются.")
                val uninstallStatus = label("Удаление не запускалось", 14f, color = accent)
                val uninstallButton = Button(this).apply {
                    text = "Удалить старый TITANIUM ULTRA"; isAllCaps = false
                    setOnClickListener {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Удалить TITANIUM ULTRA?")
                            .setMessage("С часов будет удалён пакет com.titanium.ultra. Продолжить?")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Удалить") { _, _ ->
                                run {
                                    isEnabled = false
                                    uninstallStatus.text = "Удаляем старый циферблат…"
                                    thread {
                                        val message = try {
                                            val result = WatchApkInstaller.uninstallOldTitanium(ensureWatchConnected())
                                            "TITANIUM ULTRA удалён с часов: $result"
                                        } catch (e: Exception) {
                                            android.util.Log.e("TitaniumUninstall", "Uninstall failed", e)
                                            "Ошибка удаления: " + (e.localizedMessage ?: e.javaClass.simpleName)
                                        }
                                        runOnUiThread { uninstallStatus.text = message; isEnabled = true }
                                    }
                                }
                            }
                            .show()
                    }
                }
                actionCard("×  Удалить TITANIUM ULTRA", "Удалить установленный циферблат с часов", { uninstallButton.performClick() })
                content.addView(gap(8))
                content.addView(uninstallStatus)
            }
            else -> {
                card("Профиль: Galaxy Watch Ultra", "IP, порты и защищённый ключ сохраняются. После первого сопряжения повторно вводить шестизначный код обычно не нужно. Если порт часов изменился, приложение попробует найти новый автоматически.")
                pairingForm()
                card("Безопасность", "После установки циферблата рекомендуется отключать беспроводную отладку.")
            }
        }
        content.addView(gap(12))
        content.addView(label("Версия 0.24 • Единый стиль кнопок", 12f, color = 0xff8F99AA.toInt()).apply { gravity = Gravity.CENTER })
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(10), dp(8), dp(16)); setBackgroundColor(surface) }
        listOf("◉ Каталог", "＋ APK", "⌁ Часы").forEachIndexed { index, name ->
            val button = TextView(this).apply {
                text = name; gravity = Gravity.CENTER; textSize = 14f
                setTextColor(if (index == tab) accent else 0xffB5BDCB.toInt())
                setTypeface(null, if (index == tab) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { show(index) }
            }
            nav.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        frame.addView(nav)
        setContentView(frame)
    }
    private fun pairingForm() {
        content.addView(label("Подключение часов", 22f, true))
        content.addView(label("Для повторного подключения достаточно сохранённого IP и порта ADB. Код нужен только при новом сопряжении.", 13f, color = 0xffB8C0D0.toInt()))
        content.addView(gap(16))
        val ip = EditText(this).apply {
            hint = "IP часов, например 192.168.1.20"; setText(watchAddress)
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(0xff9FA9B9.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val port = EditText(this).apply {
            hint = "Порт сопряжения"; setText(pairingPort)
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(0xff9FA9B9.toInt())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val code = EditText(this).apply {
            hint = "000000"; setSingleLine(true); textSize = 32f; gravity = Gravity.CENTER
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            setTextColor(Color.WHITE); setHintTextColor(0xff9FA9B9.toInt())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        // Preserve unsaved input when switching tabs. Pairing code is kept only in RAM.
        fun track(field: EditText, onText: (String) -> Unit) {
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onText(s?.toString() ?: "") }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        track(ip) { watchAddress = it }
        track(port) { pairingPort = it }
        track(code) { pairingCodeDraft = it }
        code.setText(pairingCodeDraft)
        val adbPort = EditText(this).apply {
            hint = "Порт подключения ADB"; setText(connectionPort)
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(0xff9FA9B9.toInt())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        track(adbPort) { connectionPort = it }
        content.addView(label("ПРОФИЛЬ ЧАСОВ · ПОРТ ADB", 16f, true, accent))
        content.addView(label("Сохранённый порт подключения. Может измениться после перезапуска беспроводной отладки.", 12f, color = 0xffB8C0D0.toInt()))
        content.addView(adbPort)
        content.addView(gap(20))
        content.addView(label("ДАННЫЕ С ЭКРАНА СОПРЯЖЕНИЯ", 16f, true, accent))
        content.addView(label("IP и временный порт рядом с кодом на часах", 12f, color = 0xffB8C0D0.toInt()))
        val pairingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ipColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ipColumn.addView(label("IP ЧАСОВ", 13f, true, accent))
        ipColumn.addView(ip)
        val pairingPortColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pairingPortColumn.addView(label("ПОРТ СОПРЯЖЕНИЯ", 13f, true, accent))
        pairingPortColumn.addView(port)
        pairingRow.addView(ipColumn, LinearLayout.LayoutParams(0, -2, 3f))
        pairingRow.addView(Space(this), LinearLayout.LayoutParams(dp(12), 1))
        pairingRow.addView(pairingPortColumn, LinearLayout.LayoutParams(0, -2, 2f))
        content.addView(pairingRow)
        content.addView(gap(20))
        content.addView(label("КОД СОПРЯЖЕНИЯ", 18f, true, accent))
        content.addView(label("6 цифр · вводится только при новом сопряжении", 12f, color = 0xffB8C0D0.toInt()))
        content.addView(code)
        content.addView(gap(14))
        content.addView(label("IP и оба порта сохраняются в профиле. Код не сохраняется. Порт подключения может меняться после перезапуска отладки.", 13f, color = 0xffB8C0D0.toInt()))
        content.addView(gap(12))
        val button = Button(this).apply {
            text = "Проверить данные"; isAllCaps = false
            setOnClickListener {
                watchAddress = ip.text.toString().trim()
                pairingPort = port.text.toString().trim()
                prefs.edit().putString("watch_ip", watchAddress).putString("pairing_port", pairingPort).putString("adb_port", adbPort.text.toString().trim()).apply()
                val validIp = watchAddress.split(".").let { parts ->
                    parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
                }
                val validPort = pairingPort.toIntOrNull()?.let { it in 1..65535 } == true
                val validCode = code.text.toString().matches(Regex("[0-9]{6}"))
                val message = if (validIp && validPort && validCode)
                    "Формат данных верный. Теперь нажми «Сопрячь часы по коду (тест)» и введи свежий код."
                else "Проверь IP, порт и шестизначный код."
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
        content.addView(button)
        content.addView(gap(10))
        val pairingStatus = label("Сопряжение ещё не выполнялось", 14f, color = accent)
        val pairButton = Button(this).apply {
            text = "Сопрячь часы по коду (тест)"; isAllCaps = false
            setOnClickListener {
                val host = ip.text.toString().trim()
                val p = port.text.toString().toIntOrNull()
                val pin = code.text.toString()
                val octets = host.split(".")
                if (octets.size != 4 || !octets.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
                    || p == null || p !in 1..65535 || !pin.matches(Regex("[0-9]{6}"))) {
                    pairingStatus.text = "Проверь IP, порт сопряжения и шестизначный код"
                } else {
                    pairingStatus.text = "Выполняется защищённое ADB-сопряжение…"
                    code.text.clear()
                    thread {
                        val message = try {
                            val manager = adbManager ?: WatchAdbManager(applicationContext).also { adbManager = it }
                            val paired = manager.pair(host, p, pin)
                            if (paired) "ADB-сопряжение выполнено. Следующий шаг — TLS-подключение."
                            else "Часы отклонили сопряжение. Проверь код и временный порт."
                        } catch (e: Exception) {
                            val chain = generateSequence<Throwable>(e) { it.cause }.take(5).joinToString(" → ") {
                                it.javaClass.simpleName + ": " + (it.localizedMessage ?: "без описания")
                            }
                            android.util.Log.e("TitaniumAdbPair", "Pairing failed", e)
                            "Ошибка ADB-сопряжения: " + chain.take(700) + "\nПроверь: открыт экран кода сопряжения на часах, введён именно временный порт сопряжения, код ещё действителен."
                        }
                        runOnUiThread { pairingStatus.text = message }
                    }
                }
            }
        }
        content.addView(pairButton)
        content.addView(gap(8))
        content.addView(pairingStatus)
        content.addView(gap(12))
        val result = label("Сеть ещё не проверялась", 14f, color = accent)
        val probe = Button(this).apply {
            text = "Проверить порт подключения"; isAllCaps = false
            setOnClickListener {
                val host = ip.text.toString().trim()
                val p = adbPort.text.toString().toIntOrNull()
                val parts = host.split(".")
                val validIp = parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
                if (!validIp || p == null || p !in 1..65535) {
                    result.text = "Проверь IP и порт подключения"
                } else {
                    watchAddress = host; connectionPort = p.toString()
                    prefs.edit().putString("watch_ip", host).putString("adb_port", connectionPort).apply()
                    result.text = "Проверяем сеть…"
                    thread {
                        val message = try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(host, p), 3000)
                                "TCP-порт доступен. ADB-сопряжение ещё не выполнено."
                            }
                        } catch (e: Exception) {
                            "Порт недоступен: " + (e.localizedMessage ?: "ошибка сети")
                        }
                        runOnUiThread { result.text = message }
                    }
                }
            }
        }
        content.addView(probe)
        content.addView(gap(8))
        content.addView(result)
        content.addView(gap(12))
        val tlsStatus = label(if (adbManager?.isConnected == true) "ADB TLS подключено к часам" else "TLS-подключение ещё не проверялось", 14f, color = accent)
        val tlsButton = Button(this).apply {
            text = "Подключиться к часам по ADB (TLS)"; isAllCaps = false
            setOnClickListener {
                val host = ip.text.toString().trim()
                val p = adbPort.text.toString().trim().toIntOrNull()
                val parts = host.split(".")
                if (parts.size != 4 || !parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
                    || p == null || p !in 1..65535) {
                    tlsStatus.text = "Проверь IP часов и порт подключения ADB (третье поле)"
                } else {
                    tlsStatus.text = "Устанавливаем защищённое ADB-соединение…"
                    thread {
                        val message = try {
                            val manager = adbManager ?: WatchAdbManager(applicationContext).also { adbManager = it }
                            if (manager.isConnected || manager.connect(host, p)) {
                                prefs.edit().putString("watch_ip", host).putString("adb_port", p.toString()).apply()
                                adbConnected = true
                                "ADB TLS подключено к часам. Можно переходить к установке APK."
                            } else {
                                "ADB TLS не подключено. Проверь IP, порт и разрешение сопряжения на часах."
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TitaniumAdbTls", "Connect failed", e)
                            "Ошибка TLS: " + (e.javaClass.simpleName + ": " + (e.localizedMessage ?: "нет описания")).take(550)
                        }
                        runOnUiThread { tlsStatus.text = message; updateConnectionStatus() }
                    }
                }
            }
        }
        content.addView(tlsButton)
        content.addView(gap(8))
        content.addView(tlsStatus)
        content.addView(gap(14))
        val liveStatus = label(if (adbManager?.isConnected == true) "● ADB подключено" else "○ ADB не подключено", 20f, true, accent)
        connectionStatusView = liveStatus
        content.addView(liveStatus)
        val statusNote = label("Профиль: Galaxy Watch Ultra · ключ хранится в зашифрованном виде", 13f, color = 0xffB8C0D0.toInt())
        connectionStatusNote = statusNote
        content.addView(statusNote)
        val refreshButton = Button(this).apply {
            text = "Проверить / восстановить подключение"; isAllCaps = false
            setOnClickListener {
                isEnabled = false
                statusNote.text = "Проверяем подключение…"
                thread {
                    val result = try { ensureWatchConnected(); "Соединение подтверждено" }
                        catch (e: Exception) { e.localizedMessage ?: "Подключение не удалось" }
                    runOnUiThread {
                        updateConnectionStatus()
                        statusNote.text = result
                        isEnabled = true
                    }
                }
            }
        }
        content.addView(gap(10))
        content.addView(refreshButton)
        content.addView(gap(16))
        val identityStatus = label("Проверка ключа не выполнялась", 14f, color = accent)
        val keyButton = Button(this).apply {
            text = "Создать защищённый ключ устройства"; isAllCaps = false
            setOnClickListener {
                identityStatus.text = "Подготавливаем ключ в Android Keystore…"
                thread {
                    val message = try {
                        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                        if (!store.containsAlias("titanium_adb_identity_v1")) {
                            val spec = KeyGenParameterSpec.Builder(
                                "titanium_adb_identity_v1",
                                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                            ).setAlgorithmParameterSpec(java.security.spec.RSAKeyGenParameterSpec(2048, java.security.spec.RSAKeyGenParameterSpec.F4))
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                                .build()
                            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                                initialize(spec)
                                generateKeyPair()
                            }
                        }
                        "Ключ создан или уже существует в Android Keystore. Сопряжение ещё не выполнено."
                    } catch (e: Exception) {
                        "Не удалось создать ключ: " + (e.localizedMessage ?: "ошибка")
                    }
                    runOnUiThread { identityStatus.text = message }
                }
            }
        }
        content.addView(keyButton)
        content.addView(gap(8))
        content.addView(identityStatus)
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

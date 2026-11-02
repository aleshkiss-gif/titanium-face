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

class MainActivity : Activity() {
    private val background = Color.rgb(11, 13, 19)
    private val surface = Color.rgb(28, 32, 42)
    private val accent = Color.rgb(114, 177, 255)
    private lateinit var content: LinearLayout
    private var selectedName: String? = null
    private var selectedSize: Long = -1
    private var selectedUri: Uri? = null
    private var currentTab = 0
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); show(0) }
    private fun show(tab: Int) {
        currentTab = tab
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(background) }
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
                card("＋  Выбрать APK", "Выбрать файл из памяти смартфона.") { pickApk() }
                if (selectedUri != null) {
                    val mb = if (selectedSize >= 0) String.format("%.2f МБ", selectedSize / 1048576.0) else "Размер неизвестен"
                    card("✓  Файл выбран", (selectedName ?: "APK") + "\n" + mb + "\n\nСовместимость с Wear OS пока не проверена.")
                } else card("Нет выбранного файла", "Выбери APK для просмотра информации.")
                card("Установка на часы", "Пока недоступна. Для установки потребуется безопасное ADB-сопряжение с часами.")
            }
            else -> {
                card("1. Включить режим разработчика", "На часах: Настройки → О часах → Сведения о ПО. Несколько раз нажми «Версия ПО», пока не появится режим разработчика.")
                card("2. Включить беспроводную отладку", "В параметрах разработчика часов включи ADB и беспроводную отладку. Часы и телефон должны быть в одной Wi-Fi сети.")
                card("3. Сопряжение", "В следующей версии добавим ввод IP, порта и кода сопряжения. Сейчас подключение ещё не выполняется.")
                card("Безопасность", "После установки циферблата рекомендуется отключать беспроводную отладку.")
            }
        }
        content.addView(gap(12))
        content.addView(label("Версия 0.2 • предварительная", 12f, color = 0xff8F99AA.toInt()).apply { gravity = Gravity.CENTER })
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
    private fun pickApk() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/vnd.android.package-archive"
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
    }
}

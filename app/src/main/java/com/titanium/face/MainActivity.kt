package com.titanium.face

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val bg = Color.rgb(12, 14, 19)
    private val surface = Color.rgb(30, 33, 41)
    private val accent = Color.rgb(105, 175, 255)
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 24) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(s: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun space(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun panel(title: String, description: String, action: (() -> Unit)? = null) {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = shape(surface) }
        p.addView(text(title, 20f, true)); p.addView(space(8)); p.addView(text(description, 14f).apply { setTextColor(0xffB9C0CE.toInt()) })
        if (action != null) { p.setOnClickListener { action() }; p.isClickable = true }
        root.addView(p, LinearLayout.LayoutParams(-1, -2)); root.addView(space(14))
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); home() }
    private fun home() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); fillViewport = true }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(38), dp(22), dp(26)) }
        scroll.addView(root); setContentView(scroll)
        root.addView(text("TITANIUM FACE", 30f, true)); root.addView(space(8))
        root.addView(text("Циферблаты для Galaxy Watch", 15f).apply { setTextColor(accent) }); root.addView(space(32))
        panel("◉  TITANIUM ULTRA", "Наш фирменный циферблат. Добавление установочного APK и превью — следующий этап.")
        panel("◷  Каталог", "Здесь будут собственные циферблаты, превью и избранное.")
        panel("＋  Выбрать APK", "Открыть файл из памяти телефона и проверить его имя.") { pickApk() }
        panel("⌁  Подключение часов", "Установка через беспроводной ADB пока не реализована. Нужны сопряжение и проверка совместимости.")
        root.addView(space(12)); root.addView(text("Версия 0.1 • прототип", 12f).apply { gravity = Gravity.CENTER; setTextColor(0xff9099AA.toInt()) })
    }
    private fun pickApk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/vnd.android.package-archive" }
        startActivityForResult(intent, 10)
    }
    @Deprecated("Legacy picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val name = uri.lastPathSegment ?: "APK"
            Toast.makeText(this, "Выбран файл: $name. Установка на часы появится позже.", Toast.LENGTH_LONG).show()
        }
    }
}
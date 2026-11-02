package com.titanium.face

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/** Imports a single APK or an APK contained in a GitHub Actions ZIP artifact. */
object ApkArchiveReader {
    private const val MAX_APK = 100L * 1024 * 1024
    private const val MAX_ZIP = 150L * 1024 * 1024
    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(16384)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= limit) { "Файл превышает допустимый размер" }
            output.write(buffer, 0, n)
        }
    }
    fun extract(context: Context, uri: Uri, filename: String?): File {
        val source = File.createTempFile("titanium-import-", ".bin", context.cacheDir)
        val target = File.createTempFile("titanium-watch-", ".apk", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output -> copyLimited(input, output, MAX_ZIP) }
            } ?: error("Не удалось открыть выбранный файл")
            val isZip = filename?.lowercase()?.endsWith(".zip") == true
            if (isZip) {
                var matches = 0
                ZipInputStream(source.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk") &&
                            !entry.name.startsWith("__MACOSX/")) {
                            matches++
                            require(matches == 1) { "В архиве несколько APK. Оставь один APK в ZIP." }
                            target.outputStream().use { output -> copyLimited(zip, output, MAX_APK) }
                        }
                        zip.closeEntry()
                    }
                }
                require(matches == 1) { "В ZIP-архиве не найден APK" }
            } else {
                require(source.length() <= MAX_APK) { "APK больше 100 МБ" }
                source.inputStream().use { input ->
                    target.outputStream().use { output -> copyLimited(input, output, MAX_APK) }
                }
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                ?: error("Файл внутри архива не распознан как APK")
            require(info.packageName != context.packageName) { "Нельзя устанавливать приложение телефона на часы" }
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            source.delete()
        }
    }
}

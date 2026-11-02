package com.titanium.face

import io.github.muntashirakon.adb.AdbStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Standard ADB sync: SEND/DATA/DONE then package-manager install.
 * All operations run off the main thread.
 */
object WatchApkInstaller {
    private const val remote = "/data/local/tmp/titanium-face-watch.apk"
    private fun packet(out: OutputStream, id: String, payload: Int) {
        require(id.length == 4)
        out.write(id.toByteArray(StandardCharsets.US_ASCII))
        out.write(byteArrayOf(payload.toByte(), (payload ushr 8).toByte(), (payload ushr 16).toByte(), (payload ushr 24).toByte()))
    }
    private fun readFully(input: InputStream, count: Int): ByteArray {
        val bytes = ByteArray(count)
        var n = 0
        while (n < count) {
            val r = input.read(bytes, n, count - n)
            if (r < 0) error("ADB sync: unexpected EOF")
            n += r
        }
        return bytes
    }
    private fun le32(bytes: ByteArray): Int =
        (bytes[0].toInt() and 255) or ((bytes[1].toInt() and 255) shl 8) or
            ((bytes[2].toInt() and 255) shl 16) or ((bytes[3].toInt() and 255) shl 24)

    fun install(manager: WatchAdbManager, apk: File, progress: (Int) -> Unit): String {
        require(manager.isConnected) { "Сначала подключись к часам по ADB TLS" }
        require(apk.isFile && apk.length() in 1..(100L * 1024 * 1024)) { "Некорректный APK" }
        val stream = manager.openStream("sync:")
        try {
            val output = stream.openOutputStream()
            val input = stream.openInputStream()
            val name = "$remote,0644".toByteArray(StandardCharsets.UTF_8)
            packet(output, "SEND", name.size)
            output.write(name)
            apk.inputStream().buffered().use { file ->
                val buffer = ByteArray(16384)
                var total = 0L
                while (true) {
                    val n = file.read(buffer)
                    if (n < 0) break
                    packet(output, "DATA", n)
                    output.write(buffer, 0, n)
                    total += n
                    progress((total * 100 / apk.length()).toInt())
                }
            }
            packet(output, "DONE", (System.currentTimeMillis() / 1000).toInt())
            output.flush()
            val status = String(readFully(input, 4), StandardCharsets.US_ASCII)
            val length = le32(readFully(input, 4))
            if (status != "OKAY") {
                val message = if (length in 1..4096) String(readFully(input, length), StandardCharsets.UTF_8) else "код $status"
                error("ADB sync: $message")
            }
        } finally {
            stream.close()
        }
        progress(101)
        val installOutput = shell(manager, "pm install -r $remote")
        try { shell(manager, "rm -f $remote") } catch (_: Exception) { }
        return installOutput
    }

    /** A TLS socket can report connected after the ADB service has stopped responding. */
    fun probe(manager: WatchAdbManager): Boolean =
        manager.isConnected && shellRaw(manager, "echo WFM_READY").contains("WFM_READY")

    fun installedPackages(manager: WatchAdbManager): Set<String> {
        require(manager.isConnected) { "Нет подключения к часам" }
        return shellRaw(manager, "pm list packages -3").lineSequence()
            .map { it.trim() }.filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }.toSet()
    }

    fun uninstallPackage(manager: WatchAdbManager, packageName: String): String {
        require(packageName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_.]*"))) { "Некорректный пакет" }
        require(packageName in installedPackages(manager)) { "Приложение уже отсутствует на часах" }
        return shell(manager, "pm uninstall $packageName")
    }

    fun uninstallOldTitanium(manager: WatchAdbManager): String {
        require(manager.isConnected) { "Сначала подключись к часам по ADB TLS" }
        return shell(manager, "pm uninstall com.titanium.ultra")
    }

    private fun shell(manager: WatchAdbManager, command: String): String {
        val result = shellRaw(manager, command)
        if (!result.contains("Success")) error("Команда не подтверждена: $result")
        return result
    }

    private fun shellRaw(manager: WatchAdbManager, command: String): String {
        val stream = manager.openStream("shell:$command")
        try {
            val input = stream.openInputStream()
            val result = StringBuilder()
            val buffer = ByteArray(1024)
            while (result.length < 8192) {
                val n = try { input.read(buffer) } catch (e: java.io.IOException) {
                    if (stream.isClosed) break else throw e
                }
                if (n < 0) break
                if (n > 0) result.append(String(buffer, 0, n, StandardCharsets.UTF_8))
            }
            return result.toString().trim()
        } finally {
            stream.close()
        }
    }
}

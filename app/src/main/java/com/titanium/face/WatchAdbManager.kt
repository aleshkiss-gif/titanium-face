package com.titanium.face

import android.os.Build
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import io.github.muntashirakon.adb.AbsAdbConnectionManager

/**
 * The same persistent AndroidKeyStore identity must be used for pairing and TLS.
 * Never regenerate it between pairing and connection.
 */
class WatchAdbManager : AbsAdbConnectionManager() {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "titanium_adb_identity_v1"

    init {
        setApi(Build.VERSION.SDK_INT)
        require(store.containsAlias(alias)) { "Сначала создай ключ устройства" }
    }

    override fun getPrivateKey(): PrivateKey =
        store.getKey(alias, null) as PrivateKey

    override fun getCertificate(): Certificate =
        store.getCertificate(alias) ?: error("Сертификат ключа отсутствует")

    override fun getDeviceName(): String = "TitaniumFace"
}

package com.titanium.face

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.KeyFactory
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ADB TLS needs an exportable RSA private key for Conscrypt.
 * Persist it encrypted with a non-exportable Android Keystore AES key.
 */
class WatchAdbManager(private val context: Context) : AbsAdbConnectionManager() {
    private val prefs = context.getSharedPreferences("adb_identity_v2", Context.MODE_PRIVATE)
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val wrappingAlias = "titanium_adb_wrap_v2"
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        val pair = loadOrCreate()
        privateKey = pair.first
        certificate = pair.second
    }

    private fun wrappingKey(): SecretKey {
        val existing = store.getKey(wrappingAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                wrappingAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadOrCreate(): Pair<PrivateKey, Certificate> {
        val encrypted = prefs.getString("key", null)
        val iv = prefs.getString("iv", null)
        val cert = prefs.getString("cert", null)
        if (encrypted != null && iv != null && cert != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey(),
                GCMParameterSpec(128, android.util.Base64.decode(iv, android.util.Base64.NO_WRAP))
            )
            val bytes = cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP))
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
            bytes.fill(0)
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                android.util.Base64.decode(cert, android.util.Base64.NO_WRAP).inputStream()
            )
            return key to certificate
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=TitaniumFace ADB")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()).abs().add(BigInteger.ONE),
            Date(now - 60_000L),
            Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
            subject,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val encoded = pair.private.encoded
        val encryptedKey = cipher.doFinal(encoded)
        val b64 = android.util.Base64.NO_WRAP
        val saved = prefs.edit()
            .putString("key", android.util.Base64.encodeToString(encryptedKey, b64))
            .putString("iv", android.util.Base64.encodeToString(cipher.iv, b64))
            .putString("cert", android.util.Base64.encodeToString(certificate.encoded, b64))
            .commit()
        if (!saved) throw IllegalStateException("Не удалось сохранить защищённый ключ ADB")
        return pair.private to certificate
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "TitaniumFace"
}

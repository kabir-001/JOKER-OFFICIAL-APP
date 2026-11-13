package com.example.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LocalIdentityKeys(
  val uid: String,
  val publicKeyBase64: String,
  val privateKey: PrivateKey,
  val keyFingerprint: String,
)

data class EncryptedEnvelope(
  val ciphertextBase64: String,
  val ivBase64: String,
  val approxBytes: Long,
)

class E2eeCryptoEngine(context: Context) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val random = SecureRandom()

  fun getOrCreateIdentityKeys(uid: String): LocalIdentityKeys {
    val privKeyName = "priv_$uid"
    val pubKeyName = "pub_$uid"
    val existingPriv = prefs.getString(privKeyName, null)
    val existingPub = prefs.getString(pubKeyName, null)

    if (!existingPriv.isNullOrBlank() && !existingPub.isNullOrBlank()) {
      try {
        val kf = KeyFactory.getInstance("EC")
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(existingPriv, Base64.NO_WRAP)))
        val pubBytes = Base64.decode(existingPub, Base64.NO_WRAP)
        kf.generatePublic(X509EncodedKeySpec(pubBytes))
        return LocalIdentityKeys(
          uid = uid,
          publicKeyBase64 = existingPub,
          privateKey = priv,
          keyFingerprint = computeKeyFingerprint(pubBytes),
        )
      } catch (_: Exception) {
        // Regenerate clean keypair below if stored entry is corrupted
      }
    }

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"), random)
    val pair = kpg.generateKeyPair()
    val privB64 = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
    val pubBytes = pair.public.encoded
    val pubB64 = Base64.encodeToString(pubBytes, Base64.NO_WRAP)

    prefs.edit()
      .putString(privKeyName, privB64)
      .putString(pubKeyName, pubB64)
      .commit()

    return LocalIdentityKeys(
      uid = uid,
      publicKeyBase64 = pubB64,
      privateKey = pair.private,
      keyFingerprint = computeKeyFingerprint(pubBytes),
    )
  }

  fun deriveSharedAesKey(
    myPrivateKey: PrivateKey,
    peerPublicKeyBase64: String,
    saltText: String,
    infoText: String = "joker-e2ee-dm-v1",
  ): SecretKeySpec {
    val peerPubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
    val peerPub: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPubBytes))
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(myPrivateKey)
    agreement.doPhase(peerPub, true)
    val sharedSecret = agreement.generateSecret()
    val aesKeyBytes = hkdfSha256(
      ikm = sharedSecret,
      salt = saltText.toByteArray(StandardCharsets.UTF_8),
      info = infoText.toByteArray(StandardCharsets.UTF_8),
      length = 32,
    )
    return SecretKeySpec(aesKeyBytes, "AES")
  }

  fun generateRandomRoomKeyEnvelope(): Pair<String, String> {
    val rawSecret = ByteArray(32).also { random.nextBytes(it) }
    val envelope = Base64.encodeToString(rawSecret, Base64.NO_WRAP)
    val fingerprint = computeKeyFingerprint(rawSecret)
    return envelope to fingerprint
  }

  fun deriveRoomAesKey(roomId: String, roomKeyEnvelope: String): SecretKeySpec {
    val raw = try {
      Base64.decode(roomKeyEnvelope, Base64.NO_WRAP)
    } catch (_: Exception) {
      roomKeyEnvelope.toByteArray(StandardCharsets.UTF_8)
    }
    val derived = hkdfSha256(
      ikm = raw,
      salt = "joker-room:$roomId".toByteArray(StandardCharsets.UTF_8),
      info = "joker-e2ee-room-v1".toByteArray(StandardCharsets.UTF_8),
      length = 32,
    )
    return SecretKeySpec(derived, "AES")
  }

  fun deriveCommunityStatusKey(statusSeed: String): SecretKeySpec {
    val derived = hkdfSha256(
      ikm = COMMUNITY_STATUS_MASTER_SEED.toByteArray(StandardCharsets.UTF_8),
      salt = "joker-status:$statusSeed".toByteArray(StandardCharsets.UTF_8),
      info = "joker-e2ee-status-v1".toByteArray(StandardCharsets.UTF_8),
      length = 32,
    )
    return SecretKeySpec(derived, "AES")
  }

  fun generateRandomStatusAesKey(): SecretKeySpec {
    val key = ByteArray(32).also { random.nextBytes(it) }
    return SecretKeySpec(key, "AES")
  }

  fun wrapStatusKeyForRecipient(
    statusAesKey: SecretKeySpec,
    myPrivateKey: PrivateKey,
    recipientUid: String,
    recipientPublicKeyBase64: String,
  ): String? {
    return try {
      val transportKey = deriveSharedAesKey(
        myPrivateKey = myPrivateKey,
        peerPublicKeyBase64 = recipientPublicKeyBase64,
        saltText = "status-key-wrap",
        infoText = "joker-status-wrap-v1",
      )
      val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, transportKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      val wrapped = cipher.doFinal(statusAesKey.encoded)
      val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
      val wrappedB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
      "$recipientUid:$ivB64:$wrappedB64"
    } catch (_: Exception) {
      null
    }
  }

  fun unwrapStatusKeyForMe(
    myUid: String,
    myPrivateKey: PrivateKey,
    authorPublicKeyBase64: String,
    wrappedKeys: List<String>,
  ): SecretKeySpec? {
    val match = wrappedKeys.firstOrNull { it.startsWith("$myUid:") } ?: return null
    val parts = match.split(":", limit = 3)
    if (parts.size != 3) return null
    return try {
      val iv = Base64.decode(parts[1], Base64.NO_WRAP)
      val wrapped = Base64.decode(parts[2], Base64.NO_WRAP)
      val transportKey = deriveSharedAesKey(
        myPrivateKey = myPrivateKey,
        peerPublicKeyBase64 = authorPublicKeyBase64,
        saltText = "status-key-wrap",
        infoText = "joker-status-wrap-v1",
      )
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, transportKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      val rawKey = cipher.doFinal(wrapped)
      SecretKeySpec(rawKey, "AES")
    } catch (_: Exception) {
      null
    }
  }

  fun encryptText(aesKey: SecretKeySpec, plaintext: String): EncryptedEnvelope {
    val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    val plainBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
    val cipherBytes = cipher.doFinal(plainBytes)
    val ciphertextB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
    return EncryptedEnvelope(
      ciphertextBase64 = ciphertextB64,
      ivBase64 = ivB64,
      approxBytes = (ciphertextB64.length + ivB64.length).toLong(),
    )
  }

  fun decryptText(aesKey: SecretKeySpec, ciphertextBase64: String, ivBase64: String): String? {
    if (ciphertextBase64.isBlank() || ivBase64.isBlank()) return null
    return try {
      val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
      val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      val plainBytes = cipher.doFinal(cipherBytes)
      String(plainBytes, StandardCharsets.UTF_8)
    } catch (_: Exception) {
      null
    }
  }

  fun computePairwiseSafetyNumber(pubKeyA: String, pubKeyB: String): String {
    val sorted = listOf(pubKeyA, pubKeyB).sorted().joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(sorted.toByteArray(StandardCharsets.UTF_8))
    val digits = buildString {
      for (i in 0 until 24) {
        val v = ((digest[i % digest.size].toInt() and 0xFF) * 39 + i) % 100
        append(v.toString().padStart(2, '0'))
      }
    }
    return digits.chunked(4).take(6).joinToString("  ")
  }

  fun anonymousGroupAlias(uid: String, roomId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$uid:$roomId".toByteArray(StandardCharsets.UTF_8))
    val hex = digest.take(2).joinToString("") { "%02X".format(it) }
    return "Member #$hex"
  }

  fun compressUriImageToLowDataBase64(uri: Uri, lowDataMode: Boolean): String? {
    return try {
      val maxDim = if (lowDataMode) 360 else 560
      var quality = if (lowDataMode) 42 else 60
      val original = appContext.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
      } ?: return null

      val scale = minOf(
        maxDim.toFloat() / original.width.coerceAtLeast(1),
        maxDim.toFloat() / original.height.coerceAtLeast(1),
        1f,
      )
      val targetW = (original.width * scale).toInt().coerceAtLeast(1)
      val targetH = (original.height * scale).toInt().coerceAtLeast(1)
      val scaled = if (scale < 1f) Bitmap.createScaledBitmap(original, targetW, targetH, true) else original

      var bytes: ByteArray
      do {
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 10
      } while (bytes.size > MAX_IMAGE_BYTES && quality >= 20)

      if (scaled !== original) scaled.recycle()
      original.recycle()
      Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
      null
    }
  }

  fun decodeBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return try {
      val bytes = Base64.decode(base64, Base64.NO_WRAP)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
      null
    }
  }

  private fun computeKeyFingerprint(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.take(6).joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")
  }

  private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val hmac = Mac.getInstance("HmacSHA256")
    val safeSalt = if (salt.isEmpty()) ByteArray(32) else salt
    hmac.init(SecretKeySpec(safeSalt, "HmacSHA256"))
    val prk = hmac.doFinal(ikm)

    val result = ByteArray(length)
    var t = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
      hmac.init(SecretKeySpec(prk, "HmacSHA256"))
      hmac.update(t)
      hmac.update(info)
      hmac.update(counter.toByte())
      t = hmac.doFinal()
      val toCopy = minOf(t.size, length - offset)
      System.arraycopy(t, 0, result, offset, toCopy)
      offset += toCopy
      counter++
    }
    return result
  }

  companion object {
    private const val PREFS_NAME = "joker_e2ee_keystore_v1"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_IMAGE_BYTES = 55_000
    private const val COMMUNITY_STATUS_MASTER_SEED = "JokerOfficial-Community-Status-E2EE-Domain-2026"
  }
}

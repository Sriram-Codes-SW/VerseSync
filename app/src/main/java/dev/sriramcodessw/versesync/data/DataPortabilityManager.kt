package dev.sriramcodessw.versesync.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DataPortabilityManager(private val context: Context) {

    private val KEY_ALIAS = "VerseSyncMasterKey"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    private val gson = Gson()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun exportLibrary(songs: List<Song>, folders: List<Folder>): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val dataMap = mapOf("songs" to songs, "folders" to folders)
        val json = gson.toJson(dataMap)
        
        val encryptedBytes = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        // Combine IV (12 bytes for GCM) and Encrypted Data for the file
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun importLibrary(encodedData: String): Pair<List<Song>, List<Folder>>? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val combined = Base64.decode(encodedData, Base64.DEFAULT)
            
            val iv = combined.sliceArray(0 until 12)
            val encryptedData = combined.sliceArray(12 until combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedJson = String(cipher.doFinal(encryptedData), Charsets.UTF_8)
            
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val dataMap: Map<String, Any> = gson.fromJson(decryptedJson, type)
            
            val songsJson = gson.toJson(dataMap["songs"])
            val foldersJson = gson.toJson(dataMap["folders"])
            
            val songs: List<Song> = gson.fromJson(songsJson, object : TypeToken<List<Song>>() {}.type)
            val folders: List<Folder> = gson.fromJson(foldersJson, object : TypeToken<List<Folder>>() {}.type)
            
            songs to folders
        } catch (e: Exception) {
            null
        }
    }
}

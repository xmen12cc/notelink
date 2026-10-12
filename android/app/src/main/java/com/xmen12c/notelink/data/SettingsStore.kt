package com.xmen12c.notelink.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SyncConfig(val username: String, val repo: String, val pat: String)

/** GitHub sync settings, stored in an encrypted prefs file. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "notelink_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): SyncConfig? {
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val repo = prefs.getString(KEY_REPO, null) ?: return null
        val pat = prefs.getString(KEY_PAT, null) ?: return null
        return SyncConfig(username, repo, pat)
    }

    fun save(config: SyncConfig?) {
        val editor = prefs.edit().clear()
        if (config != null) {
            editor.putString(KEY_USERNAME, config.username.trim())
                .putString(KEY_REPO, config.repo.trim())
                .putString(KEY_PAT, config.pat.trim())
        }
        editor.apply()
    }

    /** OAuth App client id used for the browser sign-in (not a secret). */
    fun getClientId(): String = prefs.getString(KEY_CLIENT_ID, "") ?: ""

    fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    private companion object {
        const val KEY_USERNAME = "github_username"
        const val KEY_REPO = "github_repo"
        const val KEY_PAT = "github_pat"
        const val KEY_CLIENT_ID = "oauth_client_id"
    }
}

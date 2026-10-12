package com.xmen12c.notelink.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmen12c.notelink.BuildConfig
import com.xmen12c.notelink.Graph
import com.xmen12c.notelink.data.Note
import com.xmen12c.notelink.data.SyncConfig
import com.xmen12c.notelink.sync.SyncState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Browser sign-in progress (GitHub device flow). */
sealed interface DeviceLogin {
    data object Idle : DeviceLogin
    data class CodeReady(val userCode: String, val url: String) : DeviceLogin
    data object Connecting : DeviceLogin
    data class Done(val username: String, val createdRepo: Boolean) : DeviceLogin
    data class Failed(val message: String) : DeviceLogin
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    val notes: StateFlow<List<Note>> = Graph.repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _config = MutableStateFlow(Graph.settings.load())
    val config: StateFlow<SyncConfig?> = _config.asStateFlow()

    val syncState: StateFlow<SyncState> = Graph.syncEngine.state

    private val _deviceLogin = MutableStateFlow<DeviceLogin>(DeviceLogin.Idle)
    val deviceLogin: StateFlow<DeviceLogin> = _deviceLogin.asStateFlow()

    private var scheduledSync: Job? = null

    // ---- Browser sign-in ----

    fun effectiveClientId(): String =
        Graph.settings.getClientId().ifBlank { BuildConfig.OAUTH_CLIENT_ID }

    fun saveClientId(id: String) = Graph.settings.saveClientId(id)

    fun resetDeviceLogin() {
        _deviceLogin.value = DeviceLogin.Idle
    }

    /**
     * Device flow: get a one-time code, wait for the user to approve it at
     * github.com/login/device in their browser, then fetch the account name,
     * create the private notes repo if needed, and start syncing.
     */
    fun startDeviceLogin() {
        val clientId = effectiveClientId()
        if (clientId.isBlank()) {
            _deviceLogin.value = DeviceLogin.Failed("Add your OAuth app client ID first.")
            return
        }
        viewModelScope.launch {
            try {
                val code = Graph.api.startDeviceLogin(clientId)
                _deviceLogin.value = DeviceLogin.CodeReady(code.userCode, code.verificationUri)
                val pollMs = (code.intervalSec.coerceAtLeast(3) + 1) * 1000L
                while (isActive && System.currentTimeMillis() < code.expiresAtMs) {
                    delay(pollMs)
                    val token = Graph.api.pollDeviceToken(clientId, code.deviceCode) ?: continue
                    _deviceLogin.value = DeviceLogin.Connecting
                    val login = Graph.api.getLogin(token)
                    val createdRepo = Graph.api.ensureNotesRepo(token, login, DEFAULT_REPO)
                    Graph.settings.save(SyncConfig(login, DEFAULT_REPO, token))
                    _config.value = Graph.settings.load()
                    _deviceLogin.value = DeviceLogin.Done(login, createdRepo)
                    syncNow()
                    return@launch
                }
                _deviceLogin.value = DeviceLogin.Failed("The code expired — try again.")
            } catch (e: Exception) {
                _deviceLogin.value = DeviceLogin.Failed(e.message ?: "Sign-in failed.")
            }
        }
    }

    // ---- Manual (token) configuration ----

    fun saveConfig(username: String, repo: String, pat: String) {
        val cfg = SyncConfig(username.trim(), repo.trim(), pat.trim())
        Graph.settings.save(cfg)
        _config.value = cfg
        syncNow()
    }

    fun clearConfig() {
        Graph.settings.save(null)
        _config.value = null
    }

    fun syncNow() {
        viewModelScope.launch { Graph.syncEngine.sync() }
    }

    /** Syncs after a short debounce so bursty edits produce a single push. */
    fun scheduleSync() {
        scheduledSync?.cancel()
        scheduledSync = viewModelScope.launch {
            delay(4_000)
            Graph.syncEngine.sync()
        }
    }

    fun testConnection(onResult: (String?) -> Unit) {
        val cfg = _config.value ?: run { onResult("Save your settings first"); return }
        viewModelScope.launch {
            val error = try {
                Graph.api.ping(cfg)
                null
            } catch (e: Exception) {
                e.message
            }
            onResult(error)
        }
    }

    suspend fun getNote(id: String): Note? = Graph.repository.get(id)

    fun save(id: String, title: String, body: String, pinned: Boolean, color: String) {
        viewModelScope.launch {
            val created = Graph.repository.saveDraft(id, title, body, pinned, color)
            if (created) scheduleSync()
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch {
            Graph.repository.togglePin(id)
            scheduleSync()
        }
    }

    fun setColor(id: String, color: String) {
        viewModelScope.launch {
            Graph.repository.setColor(id, color)
            scheduleSync()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            Graph.repository.delete(id)
            syncNow()
        }
    }

    private companion object {
        const val DEFAULT_REPO = "notelink-notes"
    }
}

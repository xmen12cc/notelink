package com.xmen12c.notelink.sync

import com.xmen12c.notelink.data.Note
import com.xmen12c.notelink.data.NoteFile
import com.xmen12c.notelink.data.SyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Minimal GitHub REST + OAuth device-flow client. */
class GitHubApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ContentEntry(val name: String, val path: String, val sha: String, val type: String)

    @Serializable
    private data class ContentResponse(val sha: String, val content: String? = null)

    @Serializable
    private data class PutRequest(val message: String, val content: String, val sha: String? = null)

    @Serializable
    private data class PutResponse(val content: ContentResponse)

    @Serializable
    private data class DeviceCodeResponse(
        val device_code: String,
        val user_code: String,
        val verification_uri: String,
        val expires_in: Int = 900,
        val interval: Int = 5,
    )

    @Serializable
    private data class TokenResponse(
        val access_token: String? = null,
        val error: String? = null,
        val error_description: String? = null,
    )

    @Serializable
    private data class UserResponse(val login: String)

    @Serializable
    private data class NewRepoRequest(val name: String, val private: Boolean = true, val description: String)

    /** One-time code for the browser sign-in (device flow). */
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresAtMs: Long,
        val intervalSec: Int,
    )

    private fun Request.Builder.github(cfg: SyncConfig): Request.Builder =
        header("Authorization", "Bearer ${cfg.pat}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

    private fun url(cfg: SyncConfig, path: String) =
        "https://api.github.com/repos/${cfg.username}/${cfg.repo}/contents/$path"

    /** Quick reachability/permission check; throws with a readable message on failure. */
    suspend fun ping(cfg: SyncConfig) = withContext(Dispatchers.IO) {
        val request = Request.Builder().github(cfg).url(url(cfg, "notes")).build()
        client.newCall(request).execute().use { resp ->
            when (resp.code) {
                200, 404 -> Unit // 404 just means the notes folder is empty so far
                401 -> throw IOException("Token rejected (401) — check the personal access token")
                403 -> throw IOException("Access denied (403) — token lacks read access to this repo")
                404 -> throw IOException("Repo not found (404) — check username and repo name")
                else -> throw IOException("GitHub error HTTP ${resp.code}")
            }
        }
    }

    /** Lists note files: "notes/<id>.md" -> blob sha. Empty map when the folder does not exist yet. */
    suspend fun listNotes(cfg: SyncConfig): Map<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().github(cfg).url(url(cfg, "notes")).build()
        client.newCall(request).execute().use { resp ->
            when {
                resp.code == 404 -> emptyMap()
                !resp.isSuccessful -> throw IOException("GitHub list failed: HTTP ${resp.code}")
                else -> {
                    val body = resp.body?.string() ?: throw IOException("Empty response from GitHub")
                    json.decodeFromString<List<ContentEntry>>(body)
                        .filter { it.type == "file" && it.name.endsWith(".md") }
                        .associate { it.path to it.sha }
                }
            }
        }
    }

    suspend fun getNote(cfg: SyncConfig, path: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().github(cfg).url(url(cfg, path)).build()
        client.newCall(request).execute().use { resp ->
            when {
                resp.code == 404 -> null
                !resp.isSuccessful -> throw IOException("GitHub fetch failed: HTTP ${resp.code}")
                else -> {
                    val body = resp.body?.string() ?: throw IOException("Empty response from GitHub")
                    val encoded = json.decodeFromString<ContentResponse>(body).content
                        ?: throw IOException("GitHub returned no content for $path")
                    String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
                }
            }
        }
    }

    /** Creates or updates the file; returns the new blob sha. */
    suspend fun putNote(cfg: SyncConfig, path: String, note: Note, remoteSha: String?): String =
        withContext(Dispatchers.IO) {
            val payload = PutRequest(
                message = "NoteLink sync: ${path.substringAfterLast('/')}",
                content = Base64.getEncoder().encodeToString(NoteFile.serialize(note).toByteArray(Charsets.UTF_8)),
                sha = remoteSha,
            )
            val body = json.encodeToString(PutRequest.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().github(cfg).put(body).url(url(cfg, path)).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("GitHub push failed: HTTP ${resp.code} ${resp.message}")
                }
                val text = resp.body?.string() ?: throw IOException("Empty response from GitHub")
                json.decodeFromString(PutResponse.serializer(), text).content.sha
            }
        }

    // ---- Browser sign-in (OAuth device flow) ----

    /** Step 1: ask GitHub for a one-time user code to enter at github.com/login/device. */
    suspend fun startDeviceLogin(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "repo")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub sign-in failed: HTTP ${resp.code}")
            val text = resp.body?.string() ?: throw IOException("Empty response from GitHub")
            val parsed = json.decodeFromString(DeviceCodeResponse.serializer(), text)
            DeviceCode(
                deviceCode = parsed.device_code,
                userCode = parsed.user_code,
                verificationUri = parsed.verification_uri,
                expiresAtMs = System.currentTimeMillis() + parsed.expires_in * 1000L,
                intervalSec = parsed.interval,
            )
        }
    }

    /**
     * Step 2: poll for the token. Returns null while the user hasn't finished
     * in the browser yet; throws once the code expires or is denied.
     */
    suspend fun pollDeviceToken(clientId: String, deviceCode: String): String? =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: throw IOException("Empty response from GitHub")
                val parsed = json.decodeFromString(TokenResponse.serializer(), text)
                when {
                    parsed.access_token != null -> parsed.access_token
                    parsed.error == "authorization_pending" || parsed.error == "slow_down" -> null
                    parsed.error != null -> throw IOException(
                        parsed.error_description?.replaceFirstChar { it.uppercase() } ?: parsed.error
                    )
                    else -> throw IOException("GitHub sign-in returned no token")
                }
            }
        }

    /** The signed-in account name, via the fresh token. */
    suspend fun getLogin(token: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Could not read your GitHub user: HTTP ${resp.code}")
            val text = resp.body?.string() ?: throw IOException("Empty response from GitHub")
            json.decodeFromString(UserResponse.serializer(), text).login
        }
    }

    /** Returns true if the notes repo had to be created. */
    suspend fun ensureNotesRepo(token: String, login: String, repoName: String): Boolean =
        withContext(Dispatchers.IO) {
            val head = Request.Builder()
                .url("https://api.github.com/repos/$login/$repoName")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            val exists = client.newCall(head).execute().use { it.code == 200 }
            if (exists) return@withContext false

            val payload = NewRepoRequest(
                name = repoName,
                private = true,
                description = "NoteLink notes storage (private) — created by the NoteLink app",
            )
            val body = json.encodeToString(NewRepoRequest.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val create = Request.Builder()
                .url("https://api.github.com/user/repos")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .post(body)
                .build()
            client.newCall(create).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Could not create the notes repo: HTTP ${resp.code} — create '$repoName' manually (private) and retry")
                }
            }
            true
        }
}

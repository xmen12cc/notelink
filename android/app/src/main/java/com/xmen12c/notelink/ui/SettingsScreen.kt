package com.xmen12c.notelink.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xmen12c.notelink.sync.SyncState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NotesViewModel, onClose: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val deviceLogin by vm.deviceLogin.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var clientId by rememberSaveable { mutableStateOf(vm.effectiveClientId()) }
    var editingClientId by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    // Manual token fields
    var username by remember(config) { mutableStateOf(config?.username ?: "") }
    var repo by remember(config) { mutableStateOf(config?.repo ?: "") }
    var pat by remember(config) { mutableStateOf(config?.pat ?: "") }
    var showPat by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }

    fun saveAndTest() {
        if (username.isBlank() || repo.isBlank() || pat.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Fill in all three fields first.") }
            return
        }
        busy = true
        vm.saveConfig(username, repo, pat)
        vm.testConnection { error ->
            busy = false
            scope.launch {
                snackbarHostState.showSnackbar(error ?: "Connected ✓ — starting sync. It may take a few seconds.")
            }
        }
    }

    // Open the browser automatically once a one-time code is ready.
    LaunchedEffect(deviceLogin) {
        val dl = deviceLogin
        if (dl is DeviceLogin.CodeReady) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(dl.url)))
            } catch (_: Exception) {
                // No browser? The dialog's "Open browser" button retries.
            }
        }
    }

    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = if (config == null) "Connect GitHub" else "GitHub sync") {
                if (config == null) {
                    Text(
                        "Sign in through your browser — no tokens to copy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    StatusLine("Signed in as ${config!!.username} → ${config!!.repo}", good = true)
                }

                if (!editingClientId && clientId.isBlank()) {
                    Step("1.", "On github.com open Settings → Developer settings → OAuth Apps → New OAuth App.")
                    Step("2.", "Any homepage URL, callback URL http://localhost, then register.")
                    Step("3.", "Tick \"Enable Device Flow\" and copy the Client ID.")
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("OAuth App Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            vm.saveClientId(clientId)
                            editingClientId = false
                            vm.startDeviceLogin()
                        }, enabled = clientId.isNotBlank()) { Text("Save & sign in") }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { vm.startDeviceLogin() }, enabled = clientId.isNotBlank()) {
                            if (deviceLogin is DeviceLogin.Connecting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (config == null) "Sign in with GitHub" else "Reconnect")
                            }
                        }
                        TextButton(onClick = { editingClientId = true }) {
                            Text(if (clientId.isBlank()) "Add client ID" else "Change client ID")
                        }
                    }
                    if (editingClientId) {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("OAuth App Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = {
                            vm.saveClientId(clientId)
                            editingClientId = false
                        }, enabled = clientId.isNotBlank()) { Text("Save client ID") }
                    }
                }

                when (val dl = deviceLogin) {
                    is DeviceLogin.Failed -> StatusLine(dl.message, good = false)
                    is DeviceLogin.Done -> StatusLine(
                        "Connected as ${dl.username}" + if (dl.createdRepo) " — created private repo notelink-notes" else "",
                        good = true,
                    )
                    DeviceLogin.Connecting -> StatusLine("Finishing sign-in…", good = null)
                    else -> Unit
                }
            }

            SectionCard(title = "Sync status") {
                when (val state = syncState) {
                    is SyncState.Success -> StatusLine("Last synced ${clockTime(state.at)}", good = true)
                    is SyncState.Error -> StatusLine(state.message, good = false)
                    SyncState.Syncing -> StatusLine("Syncing…", good = null)
                    SyncState.Idle -> StatusLine(if (config == null) "Not connected yet" else "Idle", good = null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.syncNow() }) { Text("Sync now") }
                    if (config != null) {
                        TextButton(onClick = { confirmDisconnect = true }) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            SectionCard(title = "Browser sign-in setup (one-time)") {
                Step("•", "Create one OAuth App on GitHub (Developer settings → OAuth Apps), tick \"Enable Device Flow\", and paste its Client ID above.")
                Step("•", "The app only asks for access to your repositories so it can create and sync the private notes repo.")
                Spacer(Modifier.height(2.dp))
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/settings/applications/new"))) },
                ) { Text("Open GitHub OAuth App page") }
            }

            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "Hide manual token setup" else "Advanced: connect with a personal access token")
            }
            if (showManual) {
                SectionCard(title = "Manual setup") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("GitHub username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text("Notes repo") },
                        supportingText = { Text("The private repo notes sync through (e.g. notelink-notes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pat,
                        onValueChange = { pat = it },
                        label = { Text("Personal access token") },
                        supportingText = { Text("Stored encrypted on this phone. Never leaves it except to GitHub.") },
                        singleLine = true,
                        visualTransformation = if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPat = !showPat }) {
                                Icon(
                                    if (showPat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPat) "Hide token" else "Show token",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { saveAndTest() }, enabled = !busy) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save & test")
                            }
                        }
                    }
                }
            }
        }
    }

    // Device-flow waiting dialog
    when (val dl = deviceLogin) {
        is DeviceLogin.CodeReady -> {
            AlertDialog(
                onDismissRequest = { vm.resetDeviceLogin() },
                title = { Text("Finish in your browser") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GitHub should have opened. Sign in and enter this one-time code:")
                        Text(
                            dl.userCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Waiting for you to approve…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(dl.url)))
                    }) { Text("Open browser again") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.resetDeviceLogin() }) { Text("Cancel") }
                },
            )
        }
        else -> Unit
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect GitHub?") },
            text = { Text("Notes already on this phone stay. Syncing pauses until you connect again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    vm.clearConfig()
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row {
        Text(
            number,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusLine(text: String, good: Boolean?) {
    val color = when (good) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

package com.dc.murmur.feature.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

@Composable
fun PermissionScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    val standardPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun hasAllStandardPermissions() = standardPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
    }

    fun hasStoragePermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()

    fun allGranted() = hasAllStandardPermissions() && hasStoragePermission()

    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allStandardGranted = results.values.all { it }
        if (allStandardGranted) {
            if (hasStoragePermission()) {
                onAllGranted()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        } else {
            showRationale = true
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (allGranted()) onAllGranted()
    }

    LaunchedEffect(Unit) {
        if (allGranted()) {
            onAllGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Murmur needs a few permissions to work",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "These are required to record audio, detect calls, send notifications, and save recordings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        PermissionRow(Icons.Default.Mic, "Microphone", "Record ambient audio")
        PermissionRow(Icons.Default.Phone, "Phone State", "Pause recording during calls")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(Icons.Default.Notifications, "Notifications", "Show recording status")
        }
        PermissionRow(Icons.Default.Storage, "All Files Access", "Save recordings to Documents")

        Spacer(Modifier.height(32.dp))

        if (showRationale) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    settingsLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Settings")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Some permissions were denied. Please grant them in Settings.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Button(
                onClick = { permissionLauncher.launch(standardPermissions) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

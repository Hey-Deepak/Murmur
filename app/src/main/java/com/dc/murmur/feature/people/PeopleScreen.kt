package com.dc.murmur.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dc.murmur.data.repository.SpeakerMatch
import com.dc.murmur.ui.components.PersonCard
import com.dc.murmur.ui.components.VoiceTagDialog
import com.dc.murmur.ui.theme.DarkSurfaceCard
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel = koinViewModel()
) {
    val taggedProfiles by viewModel.taggedProfiles.collectAsState()
    val untaggedProfiles by viewModel.untaggedProfiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val selectedSegments by viewModel.selectedProfileSegments.collectAsState()
    val enrollmentState by viewModel.enrollmentState.collectAsState()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsState()
    val playingProfileId by viewModel.playingProfileId.collectAsState()
    val mergeMode by viewModel.mergeMode.collectAsState()
    val mergeSelection by viewModel.mergeSelection.collectAsState()
    val matchSuggestions by viewModel.matchSuggestions.collectAsState()
    val coSpeakers by viewModel.coSpeakers.collectAsState()

    val taggedProfileNames = remember(taggedProfiles) {
        taggedProfiles.mapNotNull { it.label }.distinct()
    }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagDialogProfileId by remember { mutableStateOf(0L) }
    var tagDialogCurrentLabel by remember { mutableStateOf<String?>(null) }
    var tagDialogPrefilledName by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteProfileId by remember { mutableStateOf(0L) }

    // Merge suggestion state (shown after tagging when similar tagged profile exists)
    var showMergeSuggestion by remember { mutableStateOf(false) }
    var mergeSuggestionProfileId by remember { mutableStateOf(0L) }
    var mergeSuggestionMatch by remember { mutableStateOf<SpeakerMatch?>(null) }

    if (showTagDialog) {
        VoiceTagDialog(
            currentLabel = tagDialogCurrentLabel,
            prefilledName = tagDialogPrefilledName,
            onConfirm = { name ->
                viewModel.tagProfile(tagDialogProfileId, name)
                showTagDialog = false
                tagDialogPrefilledName = null
                // Check if any existing tagged profile has high similarity → suggest merge
                val suggestions = matchSuggestions[tagDialogProfileId]
                val highMatch = suggestions?.firstOrNull { it.score > 0.70f && it.profile.label != null }
                if (highMatch != null) {
                    mergeSuggestionProfileId = tagDialogProfileId
                    mergeSuggestionMatch = highMatch
                    showMergeSuggestion = true
                }
            },
            onDismiss = {
                showTagDialog = false
                tagDialogPrefilledName = null
            },
            suggestions = taggedProfileNames
        )
    }

    if (showMergeSuggestion && mergeSuggestionMatch != null) {
        val match = mergeSuggestionMatch!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMergeSuggestion = false },
            title = { Text("Similar Voice Detected") },
            text = {
                Text("This voice is similar to ${match.profile.label} (${(match.score * 100).toInt()}% match). Merge these profiles?")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.mergeProfilePair(match.profile.id, mergeSuggestionProfileId)
                    showMergeSuggestion = false
                }) { Text("Merge") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMergeSuggestion = false }) { Text("Keep Separate") }
            }
        )
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Profile") },
            text = { Text("This will remove the voice profile. Interaction history will be preserved.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deleteProfile(deleteProfileId)
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (selectedProfile != null) {
        // Detail view
        val profile = selectedProfile!!
        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Text(
                        text = profile.label ?: profile.voiceId.take(16),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        tagDialogProfileId = profile.id
                        tagDialogCurrentLabel = profile.label
                        showTagDialog = true
                    }) {
                        Icon(Icons.Default.Edit, "Edit name")
                    }
                    IconButton(onClick = {
                        deleteProfileId = profile.id
                        showDeleteConfirm = true
                    }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Stats card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Total Time", viewModel.formatDuration(profile.totalInteractionMs))
                            StatItem("Interactions", "${profile.interactionCount}")
                            StatItem("First Seen", viewModel.formatTimeSince(profile.firstSeenAt))
                        }
                    }
                }
            }

            // Voice enrollment card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (viewModel.hasVoiceEmbedding(profile)) Icons.Default.RecordVoiceOver else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (viewModel.hasVoiceEmbedding(profile)) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (viewModel.hasVoiceEmbedding(profile)) "Voice Enrolled" else "Voice Enrollment",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        when (val state = enrollmentState) {
                            is PeopleViewModel.EnrollmentState.Idle -> {
                                if (viewModel.hasVoiceEmbedding(profile)) {
                                    Text(
                                        text = "Voice print stored. This person will be recognized automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = { viewModel.startVoiceEnrollment(profile.id) }) {
                                        Text("Re-enroll Voice")
                                    }
                                } else {
                                    Text(
                                        text = "Record a voice sample to enable automatic speaker recognition.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { viewModel.startVoiceEnrollment(profile.id) }) {
                                        Icon(Icons.Default.Mic, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Enroll Voice")
                                    }
                                }
                            }
                            is PeopleViewModel.EnrollmentState.Recording -> {
                                Text(
                                    text = "Recording... Speak naturally for 15 seconds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { state.elapsedSeconds / 15f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "${state.elapsedSeconds}s / 15s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(onClick = { viewModel.stopVoiceEnrollment() }) {
                                    Text("Stop")
                                }
                            }
                            is PeopleViewModel.EnrollmentState.Processing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Extracting voice print...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            is PeopleViewModel.EnrollmentState.Success -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Voice enrolled successfully!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(onClick = { viewModel.resetEnrollmentState() }) {
                                    Text("Done")
                                }
                            }
                            is PeopleViewModel.EnrollmentState.Error -> {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(onClick = { viewModel.resetEnrollmentState() }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
            }

            // Recent interactions
            if (selectedSegments.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Interactions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                items(selectedSegments) { segment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play speaker-specific audio segments
                            val isThisPlaying = isPlaying && playingProfileId == profile.id
                            IconButton(
                                onClick = {
                                    viewModel.playSpeakerAudio(segment.chunkId, profile.id)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isThisPlaying) "Stop" else "Play",
                                    tint = if (isThisPlaying) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dateFormat.format(Date(segment.startTime)),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = viewModel.formatDuration(segment.speakingDurationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${segment.turnCount} turns",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (segment.role != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = segment.role,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (segment.emotionalState != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = segment.emotionalState,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    tagDialogProfileId = profile.id
                                    tagDialogCurrentLabel = profile.label
                                    tagDialogPrefilledName = null
                                    showTagDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit name",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // List view
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mergeMode) "Select profiles to merge" else "People",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (mergeMode) {
                        IconButton(onClick = { viewModel.toggleMergeMode() }) {
                            Icon(Icons.Default.Close, "Cancel merge")
                        }
                    } else if ((taggedProfiles.size + untaggedProfiles.size) >= 2) {
                        IconButton(onClick = { viewModel.toggleMergeMode() }) {
                            Icon(Icons.Default.CallMerge, "Merge profiles",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Merge action bar
            if (mergeMode && mergeSelection.size >= 2) {
                item {
                    Button(
                        onClick = { viewModel.executeMerge() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CallMerge, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Merge ${mergeSelection.size} profiles")
                    }
                }
            }

            // Untagged voices
            if (untaggedProfiles.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Untagged Voices (${untaggedProfiles.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(untaggedProfiles, key = { it.id }) { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mergeMode) {
                            Checkbox(
                                checked = mergeSelection.contains(profile.id),
                                onCheckedChange = { viewModel.toggleMergeSelection(profile.id) }
                            )
                        } else {
                            // Play latest interaction to preview the voice
                            val isThisPlaying = isPlaying && playingProfileId == profile.id
                            IconButton(
                                onClick = { viewModel.playLatestInteraction(profile.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isThisPlaying) "Stop" else "Listen to voice",
                                    tint = if (isThisPlaying) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        val topSuggestion = matchSuggestions[profile.id]?.firstOrNull()
                        val profileCoSpeakers = coSpeakers[profile.id] ?: emptyList()
                        PersonCard(
                            profile = profile,
                            formattedDuration = viewModel.formatDuration(profile.totalInteractionMs),
                            formattedLastSeen = viewModel.formatTimeSince(profile.lastSeenAt),
                            onClick = {
                                if (mergeMode) viewModel.toggleMergeSelection(profile.id)
                                else viewModel.selectProfile(profile.id)
                            },
                            modifier = Modifier.weight(1f),
                            matchSuggestion = topSuggestion,
                            onSuggestionTap = {
                                tagDialogProfileId = profile.id
                                tagDialogCurrentLabel = null
                                tagDialogPrefilledName = topSuggestion?.profile?.label
                                showTagDialog = true
                            },
                            coSpeakers = profileCoSpeakers
                        )
                        if (!mergeMode) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                tagDialogProfileId = profile.id
                                tagDialogCurrentLabel = null
                                tagDialogPrefilledName = null
                                showTagDialog = true
                            }) {
                                Icon(Icons.Default.Label, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tag")
                            }
                        }
                    }
                }
            }

            // Tagged people
            if (taggedProfiles.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tagged People (${taggedProfiles.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(taggedProfiles, key = { it.id }) { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mergeMode) {
                            Checkbox(
                                checked = mergeSelection.contains(profile.id),
                                onCheckedChange = { viewModel.toggleMergeSelection(profile.id) }
                            )
                        }
                        PersonCard(
                            profile = profile,
                            formattedDuration = viewModel.formatDuration(profile.totalInteractionMs),
                            formattedLastSeen = viewModel.formatTimeSince(profile.lastSeenAt),
                            onClick = {
                                if (mergeMode) viewModel.toggleMergeSelection(profile.id)
                                else viewModel.selectProfile(profile.id)
                            },
                            modifier = if (mergeMode) Modifier.weight(1f) else Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (taggedProfiles.isEmpty() && untaggedProfiles.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No voice profiles yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Record and analyze audio to detect speakers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

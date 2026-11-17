package com.example.ui.studio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.StreamQualityPreference
import com.example.data.network.LiveCheckResult
import com.example.data.network.LiveCreatorInfo
import com.example.service.RecordingPhase
import com.example.service.RecordingSessionState
import com.example.ui.components.Media3VideoPlayer
import com.example.ui.theme.AmberStatus
import com.example.ui.theme.AmberStatusContainer
import com.example.ui.theme.CrimsonLive
import com.example.ui.theme.CrimsonLiveContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.CyanSignalContainer
import com.example.ui.theme.MonoStatTextStyle
import com.example.ui.theme.MonoTimerTextStyle
import com.example.ui.util.Formatters

@Composable
fun StudioScreen(
    uiState: StudioUiState,
    onUsernameChange: (String) -> Unit,
    onCheckLiveStatus: () -> Unit,
    onSelectRecentUsername: (String) -> Unit,
    onSelectQuality: (StreamQualityPreference) -> Unit,
    onStartRecording: (LiveCreatorInfo) -> Unit,
    onStopRecording: () -> Unit,
    onTogglePreviewMute: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenSavedRecording: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var pendingStartCreator by remember { mutableStateOf<LiveCreatorInfo?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingStartCreator?.let { creator ->
            onStartRecording(creator)
            pendingStartCreator = null
        }
    }

    val startRecordingWithPermission: (LiveCreatorInfo) -> Unit = { creator ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onStartRecording(creator)
            } else {
                pendingStartCreator = creator
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onStartRecording(creator)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StudioHeroBanner(isRecording = uiState.recordingState.isBusy)

            RecordingOutcomeBanner(
                phase = uiState.recordingState.phase,
                onWatchOffline = onOpenSavedRecording,
                onDismiss = onDismissBanner
            )

            val activeCreator = uiState.recordingState.activeCreator
            if (uiState.recordingState.isBusy && activeCreator != null) {
                ActiveRecordingStudioPanel(
                    creator = activeCreator,
                    recordingState = uiState.recordingState,
                    isPreviewMuted = uiState.isPreviewMuted,
                    onToggleMute = onTogglePreviewMute,
                    onStopRecording = onStopRecording
                )
            } else {
                UsernameCheckerCard(
                    usernameInput = uiState.usernameInput,
                    recentUsernames = uiState.recentUsernames,
                    preferredQuality = uiState.preferredQuality,
                    isChecking = uiState.checkState is CheckUiState.Checking,
                    onUsernameChange = onUsernameChange,
                    onCheckClick = {
                        keyboardController?.hide()
                        onCheckLiveStatus()
                    },
                    onSelectRecent = { user ->
                        keyboardController?.hide()
                        onSelectRecentUsername(user)
                    },
                    onSelectQuality = onSelectQuality
                )

                LiveStatusResultSection(
                    checkState = uiState.checkState,
                    isPreviewMuted = uiState.isPreviewMuted,
                    onToggleMute = onTogglePreviewMute,
                    onRetryCheck = onCheckLiveStatus,
                    onStartRecording = startRecordingWithPermission
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudioHeroBanner(isRecording: Boolean) {
    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.38f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LiveTv,
                            contentDescription = null,
                            tint = if (isRecording) CrimsonLive else CyanSignal,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.studio_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.studio_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isRecording) CrimsonLiveContainer else CyanSignalContainer,
                    border = BorderStroke(
                        1.dp,
                        if (isRecording) CrimsonLive else CyanSignal.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = if (isRecording) "CAPTURING" else "MP4 READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecording) CrimsonLive else CyanSignal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingOutcomeBanner(
    phase: RecordingPhase,
    onWatchOffline: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = phase is RecordingPhase.Saved || phase is RecordingPhase.Failed,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        when (phase) {
            is RecordingPhase.Saved -> {
                val rec = phase.recording
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CyanSignalContainer),
                    border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recording_saved_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Saved",
                            tint = CyanSignal,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saved ${rec.fileName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${rec.username} • ${Formatters.formatDuration(rec.durationMs)} • ${Formatters.formatBytes(rec.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanSignal
                            )
                        }
                        TextButton(
                            onClick = { onWatchOffline(rec.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("banner_watch_offline_button")
                        ) {
                            Text("Watch", color = CyanSignal, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            is RecordingPhase.Failed -> {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CrimsonLiveContainer),
                    border = BorderStroke(1.dp, CrimsonLive.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Recording error",
                            tint = CrimsonLive
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = phase.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun UsernameCheckerCard(
    usernameInput: String,
    recentUsernames: List<String>,
    preferredQuality: StreamQualityPreference,
    isChecking: Boolean,
    onUsernameChange: (String) -> Unit,
    onCheckClick: () -> Unit,
    onSelectRecent: (String) -> Unit,
    onSelectQuality: (StreamQualityPreference) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = usernameInput,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.username_label)) },
                placeholder = { Text(stringResource(R.string.username_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.PersonSearch,
                        contentDescription = null,
                        tint = CyanSignal
                    )
                },
                trailingIcon = {
                    if (usernameInput.isNotEmpty()) {
                        IconButton(
                            onClick = { onUsernameChange("") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear username"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onCheckClick() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanSignal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = CyanSignal
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.stream_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamQualityPreference.entries.forEach { quality ->
                    val selected = quality == preferredQuality
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectQuality(quality) },
                        label = {
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanSignalContainer,
                            selectedLabelColor = CyanSignal
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quality_chip_${quality.name.lowercase()}")
                    )
                }
            }

            Button(
                onClick = onCheckClick,
                enabled = !isChecking && usernameInput.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanSignal,
                    contentColor = Color(0xFF00201C)
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("check_live_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Color(0xFF00201C),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.checking_status), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.check_live_status), fontWeight = FontWeight.Bold)
                }
            }

            if (recentUsernames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.recent_creators),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentUsernames, key = { it }) { recent ->
                            AssistChip(
                                onClick = { onSelectRecent(recent) },
                                label = { Text("@$recent") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusResultSection(
    checkState: CheckUiState,
    isPreviewMuted: Boolean,
    onToggleMute: () -> Unit,
    onRetryCheck: () -> Unit,
    onStartRecording: (LiveCreatorInfo) -> Unit
) {
    when (checkState) {
        CheckUiState.Idle -> {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How LiveVault Works",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1. Enter any TikTok @username and check if they are streaming live right now.\n2. Once verified online, tap Start Recording to capture the stream locally as an MP4.\n3. Watch the live preview with a real-time timer and get an alert notification as soon as your MP4 finishes saving.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is CheckUiState.Checking -> {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = CyanSignal,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Checking @${checkState.username}…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Inspecting TikTok live room status and stream feeds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        is CheckUiState.Result -> {
            when (val res = checkState.result) {
                is LiveCheckResult.Online -> {
                    OnlineCreatorCard(
                        info = res.info,
                        isPreviewMuted = isPreviewMuted,
                        onToggleMute = onToggleMute,
                        onStartRecording = { onStartRecording(res.info) }
                    )
                }
                is LiveCheckResult.Offline -> {
                    OfflineCreatorCard(
                        username = res.username,
                        displayName = res.displayName,
                        avatarUrl = res.avatarUrl,
                        reason = res.reason,
                        onRetry = onRetryCheck
                    )
                }
                is LiveCheckResult.NotFound -> {
                    ErrorStatusCard(
                        title = "Username Not Found",
                        message = res.reason,
                        onRetry = onRetryCheck
                    )
                }
                is LiveCheckResult.Error -> {
                    ErrorStatusCard(
                        title = "Connection Issue",
                        message = res.reason,
                        onRetry = onRetryCheck
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineCreatorCard(
    info: LiveCreatorInfo,
    isPreviewMuted: Boolean,
    onToggleMute: () -> Unit,
    onStartRecording: () -> Unit
) {
    var showPreRecordPreview by remember(info.roomId) { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, CyanSignal.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_creator_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (info.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = info.avatarUrl,
                        contentDescription = "${info.displayName} avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(2.dp, CyanSignal, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(CyanSignalContainer)
                            .border(2.dp, CyanSignal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = info.username.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = CyanSignal
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${info.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = CrimsonLiveContainer,
                    border = BorderStroke(1.dp, CrimsonLive)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CrimsonLive)
                        )
                        Text(
                            text = stringResource(R.string.status_live),
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonLive
                        )
                    }
                }
            }

            Text(
                text = info.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = CyanSignal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${Formatters.formatViewerCount(info.viewerCount)} watching",
                            style = MonoStatTextStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = info.selectedQualityLabel,
                        style = MonoStatTextStyle,
                        color = CyanSignal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (showPreRecordPreview && !info.previewStreamUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 12f)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                ) {
                    Media3VideoPlayer(
                        mediaUri = info.previewStreamUrl!!,
                        isLiveStream = true,
                        isMuted = isPreviewMuted,
                        showControls = false,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPreviewMuted) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = stringResource(
                                if (isPreviewMuted) R.string.unmute_preview else R.string.mute_preview
                            ),
                            tint = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showPreRecordPreview = !showPreRecordPreview },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (showPreRecordPreview) "Hide Preview" else "Preview")
                }

                Button(
                    onClick = onStartRecording,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonLive,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .testTag("start_recording_button")
                ) {
                    Icon(Icons.Rounded.FiberManualRecord, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.start_recording),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineCreatorCard(
    username: String,
    displayName: String?,
    avatarUrl: String?,
    reason: String,
    onRetry: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AmberStatus.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_creator_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "$username avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AmberStatusContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SignalWifiOff,
                            contentDescription = null,
                            tint = AmberStatus
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: "@$username",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = AmberStatusContainer,
                    border = BorderStroke(1.dp, AmberStatus.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = stringResource(R.string.status_offline),
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberStatus,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("recheck_status_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check Again")
            }
        }
    }
}

@Composable
private fun ErrorStatusCard(
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@Composable
private fun ActiveRecordingStudioPanel(
    creator: LiveCreatorInfo,
    recordingState: RecordingSessionState,
    isPreviewMuted: Boolean,
    onToggleMute: () -> Unit,
    onStopRecording: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, CrimsonLive),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_recording_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live video preview container with HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            ) {
                val previewUrl = creator.previewStreamUrl
                if (!previewUrl.isNullOrBlank()) {
                    Media3VideoPlayer(
                        mediaUri = previewUrl,
                        isLiveStream = true,
                        isMuted = isPreviewMuted,
                        showControls = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top HUD overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CrimsonLive.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Text(
                                text = stringResource(R.string.status_recording),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "@${creator.username}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )

                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("mute_preview_button")
                    ) {
                        Icon(
                            imageVector = if (isPreviewMuted) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = stringResource(
                                if (isPreviewMuted) R.string.unmute_preview else R.string.mute_preview
                            ),
                            tint = Color.White
                        )
                    }
                }

                // Bottom-left Live Preview Timer Pill inside video overlay
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.74f),
                    border = BorderStroke(1.dp, CrimsonLive.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = Formatters.formatDuration(recordingState.elapsedMs),
                        style = MonoTimerTextStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Telemetry & Counter Strip
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ELAPSED TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatDuration(recordingState.elapsedMs),
                            style = MonoTimerTextStyle,
                            color = CrimsonLive,
                            modifier = Modifier.testTag("recording_timer_text")
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CAPTURED MP4",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatBytes(recordingState.bytesWritten),
                            style = MonoStatTextStyle.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyanSignal
                        )
                        Text(
                            text = "${recordingState.segmentsProcessed} segments • ${creator.selectedQualityLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val isFinalizing = recordingState.phase is RecordingPhase.Finalizing
            Button(
                onClick = onStopRecording,
                enabled = !isFinalizing,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonLive,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("stop_recording_button")
            ) {
                if (isFinalizing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Finalizing MP4…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stop_and_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

package com.flowxperts.reflexledcontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel

// Modern Color Palette
val PrimaryGold = Color(0xFFD4AF37)
val DarkGrey = Color(0xFF121212)
val SurfaceGrey = Color(0xFF1E1E1E)
val AccentGold = Color(0xFFFFD700)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val customColorScheme = darkColorScheme(
                primary = PrimaryGold,
                onPrimary = Color.Black,
                surface = SurfaceGrey,
                background = DarkGrey,
                secondary = AccentGold
            )
            MaterialTheme(colorScheme = customColorScheme) {
                LanBoxAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanBoxAppScreen(viewModel: LanBoxViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("LanBoxPrefs", Context.MODE_PRIVATE)

    val defaultIp = stringResource(R.string.default_ip)
    val defaultPass = stringResource(R.string.default_pass)

    var ipAddress by remember { mutableStateOf(sharedPrefs.getString("IP", defaultIp) ?: defaultIp) }
    var password by remember { mutableStateOf(sharedPrefs.getString("PASS", defaultPass) ?: defaultPass) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ipAddress, password) {
        viewModel.connect(ipAddress, password)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img),
                            contentDescription = "Header Logo",
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(32.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = stringResource(R.string.app_title).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, CueListEditorActivity::class.java))
                    }) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit Cue List",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, Color.Black)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- CONNECTION STATUS ---
            StatusBanner(viewModel)

            // --- SEQUENCE CONTROL CARD ---
            SequenceCard(viewModel)

            // --- PLAYBACK CONTROLS ---
            PlaybackSection(viewModel)
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            ip = ipAddress,
            pass = password,
            onDismiss = { showSettingsDialog = false },
            onSave = { newIp, newPass ->
                sharedPrefs.edit {
                    putString("IP", newIp)
                    putString("PASS", newPass)
                }
                ipAddress = newIp
                password = newPass
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun StatusBanner(viewModel: LanBoxViewModel) {
    val statusColor by animateColorAsState(
        targetValue = when {
            viewModel.isConnecting -> Color.Gray
            viewModel.isConnected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(500), label = ""
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = viewModel.connectionStatus,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.weight(1f))
        if (viewModel.isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = statusColor)
        } else {
            Icon(
                imageVector = if (viewModel.isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = statusColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequenceCard(viewModel: LanBoxViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.label_sequence), Icons.AutoMirrored.Rounded.FormatListBulleted)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                val selectCueListStr = stringResource(R.string.select_cue_list)
                var selectedCue by remember { mutableStateOf(selectCueListStr) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedCue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.available_cue_lists)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (viewModel.availableCueLists.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_cue_lists_found)) },
                                onClick = { expanded = false }
                            )
                        } else {
                            viewModel.availableCueLists.forEach { cue ->
                                val cueListItemStr = stringResource(R.string.cue_list_item, cue)
                                DropdownMenuItem(
                                    text = { Text(cueListItemStr) },
                                    onClick = {
                                        selectedCue = cueListItemStr
                                        expanded = false
                                        viewModel.goCueList(cue)
                                    }
                                )
                            }
                        }
                    }
                }

                val speedPercent = (viewModel.currentSpeed / 255f * 100).toInt()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speed Control",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "$speedPercent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = viewModel.currentSpeed,
                        onValueChange = { viewModel.setSpeed(it) },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun PlaybackSection(viewModel: LanBoxViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.label_playback), Icons.Rounded.PlayCircleOutline)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            item { PlaybackBtn(Icons.Rounded.Pause, stringResource(R.string.btn_pause), MaterialTheme.colorScheme.surface) { viewModel.sendCommand("58$LAYER_A") } }
            item { PlaybackBtn(Icons.Rounded.PlayArrow, stringResource(R.string.btn_resume), MaterialTheme.colorScheme.primary) { viewModel.sendCommand("59$LAYER_A") } }
            item { PlaybackBtn(Icons.Rounded.SkipPrevious, stringResource(R.string.btn_prev_step), MaterialTheme.colorScheme.surface) { viewModel.sendCommand("5B$LAYER_A") } }
            item { PlaybackBtn(Icons.Rounded.SkipNext, stringResource(R.string.btn_next_step), MaterialTheme.colorScheme.surface) { viewModel.sendCommand("5A$LAYER_A") } }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.sendCommand("57$LAYER_A") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF442222),
                contentColor = Color(0xFFFF8888)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.LayersClear, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.btn_clear_layer).uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun PlaybackBtn(icon: ImageVector, label: String, containerColor: Color, onClick: () -> Unit) {
    val contentColor = if (containerColor == MaterialTheme.colorScheme.primary) Color.Black else Color.White

    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsDialog(ip: String, pass: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var tempIp by remember { mutableStateOf(ip) }
    var tempPass by remember { mutableStateOf(pass) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceGrey,
        title = { Text(text = stringResource(R.string.settings_title), color = PrimaryGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text(text = stringResource(R.string.ip_address_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGold)
                )
                OutlinedTextField(
                    value = tempPass,
                    onValueChange = { tempPass = it },
                    label = { Text(text = stringResource(R.string.password_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGold)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(tempIp, tempPass) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color.Black)
            ) {
                Text(text = stringResource(R.string.btn_save_reconnect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel), color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}
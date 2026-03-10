package com.flowxperts.reflexledcontrol

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.*

val PrimaryGold = Color(0xFFD4AF37)
val DarkGrey = Color(0xFF121212)
val SurfaceGrey = Color(0xFF1E1E1E)
val AccentGold = Color(0xFFFFD700)

data class LedStep(val colors: List<Color>)

private const val PALETTE_KEY = "colorPalette"

@OptIn(ExperimentalMaterial3Api::class)
class CueListEditorActivity : ComponentActivity() {
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
                CueListEditorScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CueListEditorScreen(onBack: () -> Unit, viewModel: LanBoxViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sharedPrefs = context.getSharedPreferences("LanBoxPrefs", Context.MODE_PRIVATE)

    // Load expert settings / custom mapping
    val numLeds = sharedPrefs.getInt("NUM_LEDS", 10)
    val startChannel = sharedPrefs.getInt("START_CHANNEL", 1)
    val channelGap = sharedPrefs.getInt("CHANNEL_GAP", 0)
    val customMapping = sharedPrefs.getString("CUSTOM_MAPPING", "") ?: ""

    val addressMap = remember(customMapping, numLeds, startChannel, channelGap) {
        if (customMapping.isNotBlank()) {
            customMapping.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (parts.size >= 3) Triple(parts[0], parts[1], parts[2]) else null
            }
        } else {
            List(numLeds) { i ->
                val base = startChannel + (i * (3 + channelGap))
                Triple(base, base + 1, base + 2)
            }
        }
    }

    // Load and persist color palette
    val defaultPalette = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.White, Color.Black)
    val colorPalette = remember {
        val saved = sharedPrefs.getStringSet(PALETTE_KEY, null)
        val initial = if (saved != null) {
            saved.mapNotNull {
                try { Color(it.toInt()) } catch (e: Exception) { null }
            }
        } else defaultPalette
        
        mutableStateListOf(*initial.toTypedArray())
    }

    LaunchedEffect(colorPalette.toList()) {
        val stringSet = colorPalette.map { it.toArgb().toString() }.toSet()
        sharedPrefs.edit().putStringSet(PALETTE_KEY, stringSet).apply()
    }

    var steps by remember { mutableStateOf(listOf(LedStep(List(addressMap.size) { Color.Black }))) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var selectedColor by remember { mutableStateOf(colorPalette.firstOrNull() ?: Color.White) }
    
    // Ensure selected color is valid if palette shrinks
    if (selectedColor !in colorPalette && colorPalette.isNotEmpty()) {
        selectedColor = colorPalette.first()
    }

    var showColorDialog by remember { mutableStateOf(false) }
    var isPreviewing by remember { mutableStateOf(false) }
    
    // State to track which LED is currently being flashed
    var flashingLedIndex by remember { mutableStateOf<Int?>(null) }
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // DMX Preview Loop
    LaunchedEffect(isPreviewing) {
        if (isPreviewing) {
            while (isPreviewing) {
                val step = steps.getOrNull(currentStepIndex) ?: continue
                step.colors.forEachIndexed { i, color ->
                    val addr = addressMap.getOrNull(i) ?: return@forEachIndexed
                    viewModel.sendDmx(addr.first, listOf((color.red * 255).toInt()))
                    viewModel.sendDmx(addr.second, listOf((color.green * 255).toInt()))
                    viewModel.sendDmx(addr.third, listOf((color.blue * 255).toInt()))
                }
                delay(500)
                currentStepIndex = (currentStepIndex + 1) % steps.size
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkGrey),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = PrimaryGold) }
                },
                title = { Text("RGB STRIP EDITOR", style = MaterialTheme.typography.titleMedium, color = PrimaryGold, letterSpacing = 2.sp) },
                actions = {
                    IconButton(onClick = { isPreviewing = !isPreviewing }) {
                        Icon(
                            if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            "Preview",
                            tint = if (isPreviewing) Color.Red else PrimaryGold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkGrey, Color.Black)))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Step Navigation ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (currentStepIndex > 0) currentStepIndex-- }) {
                        Icon(Icons.Rounded.ChevronLeft, "Prev Step", tint = PrimaryGold)
                    }
                    Text("STEP ${currentStepIndex + 1} / ${steps.size}", fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = { if (currentStepIndex < steps.size - 1) currentStepIndex++ }) {
                        Icon(Icons.Rounded.ChevronRight, "Next Step", tint = PrimaryGold)
                    }
                }
                
                if (steps.size > 1) {
                    IconButton(onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.removeAt(currentStepIndex)
                        steps = newSteps
                        if (currentStepIndex >= steps.size) currentStepIndex = steps.size - 1
                    }) {
                        Icon(Icons.Rounded.Delete, "Delete Step", tint = Color.Red)
                    }
                }
            }

            // --- Color Palette (Two lines, Max 16 items) ---
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 8
            ) {
                colorPalette.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (selectedColor == color) 2.dp else 1.dp, if (selectedColor == color) Color.White else Color.Transparent, CircleShape)
                            .pointerInput(color) {
                                detectTapGestures(
                                    onTap = { selectedColor = color },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (colorPalette.size > 1) colorPalette.remove(color)
                                    }
                                )
                            }
                    )
                }
                if (colorPalette.size < 16) {
                    IconButton(
                        onClick = { showColorDialog = true },
                        modifier = Modifier.size(36.dp).border(1.dp, PrimaryGold, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Add, "Add Color", tint = PrimaryGold)
                    }
                }
            }
            Text("Tip: Long press a color to delete it.", fontSize = 10.sp, color = Color.Gray)

            // --- LED Grid ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isLandscape) 32.dp else 40.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentColors = steps.getOrNull(currentStepIndex)?.colors ?: emptyList()
                itemsIndexed(currentColors) { index, color ->
                    val isFlashing = flashingLedIndex == index
                    val displayColor = if (isFlashing) Color.White else color
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 28.dp else 36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(displayColor)
                                .border(
                                    width = if (isFlashing) 2.dp else 1.dp,
                                    color = if (isFlashing) PrimaryGold else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(index) {
                                    detectTapGestures(
                                        onPress = {
                                            val addr = addressMap.getOrNull(index) ?: return@detectTapGestures
                                            
                                            // Visual and Haptic Feedback
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            flashingLedIndex = index
                                            
                                            // Send Flash Command (Full White)
                                            viewModel.sendDmx(addr.first, listOf(255))
                                            viewModel.sendDmx(addr.second, listOf(255))
                                            viewModel.sendDmx(addr.third, listOf(255))
                                            
                                            tryAwaitRelease()
                                            
                                            // Remove Visual Feedback
                                            flashingLedIndex = null
                                            
                                            // Restore original state from the current step
                                            val c = steps[currentStepIndex].colors[index]
                                            viewModel.sendDmx(addr.first, listOf((c.red * 255).toInt()))
                                            viewModel.sendDmx(addr.second, listOf((c.green * 255).toInt()))
                                            viewModel.sendDmx(addr.third, listOf((c.blue * 255).toInt()))
                                        },
                                        onTap = {
                                            val newSteps = steps.toMutableList()
                                            val stepColors = newSteps[currentStepIndex].colors.toMutableList()
                                            stepColors[index] = selectedColor
                                            newSteps[currentStepIndex] = LedStep(stepColors)
                                            steps = newSteps
                                            
                                            // Send new color DMX
                                            val addr = addressMap.getOrNull(index) ?: return@detectTapGestures
                                            viewModel.sendDmx(addr.first, listOf((selectedColor.red * 255).toInt()))
                                            viewModel.sendDmx(addr.second, listOf((selectedColor.green * 255).toInt()))
                                            viewModel.sendDmx(addr.third, listOf((selectedColor.blue * 255).toInt()))
                                        }
                                    )
                                }
                        )
                        if (!isLandscape) {
                            Text(text = "${index + 1}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // --- Step Actions ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.add(currentStepIndex + 1, LedStep(List(addressMap.size) { Color.Black }))
                        steps = newSteps
                        currentStepIndex++
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceGrey, contentColor = PrimaryGold)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("ADD STEP")
                }
                Button(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.add(currentStepIndex + 1, LedStep(steps[currentStepIndex].colors))
                        steps = newSteps
                        currentStepIndex++
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null)
                    Spacer(Modifier.width(4.dp))
                    Text("DUPLICATE")
                }
            }

            // --- Shift Actions ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        val currentColors = newSteps[currentStepIndex].colors.toMutableList()
                        if (currentColors.isNotEmpty()) {
                            val last = currentColors.removeAt(currentColors.size - 1)
                            currentColors.add(0, last)
                            newSteps[currentStepIndex] = LedStep(currentColors)
                            steps = newSteps
                        }
                    }, 
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceGrey, contentColor = Color.White)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
                    Spacer(Modifier.width(4.dp))
                    Text("REVERSE")
                }
                
                FilledTonalButton(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        val currentColors = newSteps[currentStepIndex].colors.toMutableList()
                        if (currentColors.isNotEmpty()) {
                            val first = currentColors.removeAt(0)
                            currentColors.add(first)
                            newSteps[currentStepIndex] = LedStep(currentColors)
                            steps = newSteps
                        }
                    }, 
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceGrey, contentColor = Color.White)
                ) {
                    Text("FORWARD")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }

            Button(
                onClick = { /* LanBox sequence save logic here */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color.Black)
            ) {
                Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("SAVE SEQUENCE")
            }
        }
    }

    if (showColorDialog) {
        ColorPickerDialog(
            onDismiss = { showColorDialog = false },
            onColorAdded = { color ->
                if (colorPalette.size < 16) {
                    colorPalette.add(color)
                    selectedColor = color
                }
                showColorDialog = false
            }
        )
    }
}

@Composable
fun ColorPickerDialog(onDismiss: () -> Unit, onColorAdded: (Color) -> Unit) {
    var rText by remember { mutableStateOf("255") }
    var gText by remember { mutableStateOf("255") }
    var bText by remember { mutableStateOf("255") }

    val currentColor = remember(rText, gText, bText) {
        val r = rText.toIntOrNull()?.coerceIn(0, 255) ?: 255
        val g = gText.toIntOrNull()?.coerceIn(0, 255) ?: 255
        val b = bText.toIntOrNull()?.coerceIn(0, 255) ?: 255
        Color(r, g, b)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceGrey,
        title = { Text("Add Color Preset", color = PrimaryGold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Preview & RGB Inputs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColor)
                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = rText,
                            onValueChange = { rText = it },
                            label = { Text("R", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        OutlinedTextField(
                            value = gText,
                            onValueChange = { gText = it },
                            label = { Text("G", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        OutlinedTextField(
                            value = bText,
                            onValueChange = { bText = it },
                            label = { Text("B", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }
                }

                // Color Wheel with Tap and Drag Gestures
                ColorWheel(
                    modifier = Modifier.size(200.dp),
                    onColorChanged = { newColor ->
                        rText = (newColor.red * 255).toInt().toString()
                        gText = (newColor.green * 255).toInt().toString()
                        bText = (newColor.blue * 255).toInt().toString()
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorAdded(currentColor) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color.Black)
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

@Composable
fun ColorWheel(modifier: Modifier = Modifier, onColorChanged: (Color) -> Unit) {
    Canvas(modifier = modifier
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val distance = sqrt((offset.x - centerX).pow(2) + (offset.y - centerY).pow(2))
                val radius = min(centerX, centerY)
                
                if (distance <= radius) {
                    val angle = atan2(offset.y - centerY, offset.x - centerX) * 180 / PI
                    val normalizedAngle = if (angle < 0) angle + 360.0 else angle
                    onColorChanged(Color.hsv(normalizedAngle.toFloat(), (distance / radius).toFloat(), 1f))
                }
            }
        }
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val offset = change.position
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val distance = sqrt((offset.x - centerX).pow(2) + (offset.y - centerY).pow(2))
                val radius = min(centerX, centerY)
                
                if (distance <= radius) {
                    val angle = atan2(offset.y - centerY, offset.x - centerX) * 180 / PI
                    val normalizedAngle = if (angle < 0) angle + 360.0 else angle
                    onColorChanged(Color.hsv(normalizedAngle.toFloat(), (distance / radius).toFloat(), 1f))
                }
            }
        }
    ) {
        val radius = min(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
            ),
            radius = radius
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                radius = radius
            )
        )
    }
}

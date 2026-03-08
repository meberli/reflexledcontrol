package com.flowxperts.reflexledcontrol

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LedStep(
    val colors: List<Color>
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CueListEditorScreen(onBack: () -> Unit, viewModel: LanBoxViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("LanBoxPrefs", Context.MODE_PRIVATE)

    var numLeds by remember { mutableIntStateOf(sharedPrefs.getInt("NUM_LEDS", 10)) }
    var startChannel by remember { mutableIntStateOf(sharedPrefs.getInt("START_CHANNEL", 1)) }
    var steps by remember { mutableStateOf(listOf(LedStep(List(numLeds) { Color.Black }))) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    
    var colorPalette by remember { 
        mutableStateOf(listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.White, Color.Black))
    }
    var selectedColor by remember { mutableStateOf(colorPalette[0]) }
    
    var showFixtureSettings by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var isPreviewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Preview Logic
    LaunchedEffect(isPreviewing) {
        if (isPreviewing) {
            while (isPreviewing) {
                val step = steps[currentStepIndex]
                val dmxValues = step.colors.flatMap { listOf((it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                viewModel.sendDmx(startChannel, dmxValues)
                
                delay(500) // 500ms between steps
                currentStepIndex = (currentStepIndex + 1) % steps.size
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkGrey),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                    }
                },
                title = {
                    Text("RGB STRIP EDITOR", style = MaterialTheme.typography.titleMedium, color = PrimaryGold, letterSpacing = 2.sp)
                },
                actions = {
                    IconButton(onClick = { isPreviewing = !isPreviewing }) {
                        Icon(
                            if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = "Preview",
                            tint = if (isPreviewing) Color.Red else PrimaryGold
                        )
                    }
                    IconButton(onClick = { showFixtureSettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Fixture Settings", tint = PrimaryGold)
                    }
                }
            )
        }
    ) { padding ->
        val editorContent = @Composable { modifier: Modifier ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(DarkGrey, Color.Black)))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (currentStepIndex > 0) currentStepIndex-- }) {
                            Icon(Icons.Rounded.ChevronLeft, "Prev Step", tint = PrimaryGold)
                        }
                        Text("STEP ${currentStepIndex + 1} / ${steps.size}", fontWeight = FontWeight.Bold)
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

                // Color Palette
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(colorPalette) { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (selectedColor == color) 2.dp else 1.dp, Color.White, CircleShape)
                                    .clickable { selectedColor = color }
                            )
                        }
                    }
                    IconButton(onClick = { showColorDialog = true }) {
                        Icon(Icons.Rounded.Palette, "Manage Colors", tint = PrimaryGold)
                    }
                }

                // LED Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isLandscape) 32.dp else 40.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(steps[currentStepIndex].colors) { index, color ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(if (isLandscape) 28.dp else 36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        val newSteps = steps.toMutableList()
                                        val currentStepColors = steps[currentStepIndex].colors.toMutableList()
                                        currentStepColors[index] = selectedColor
                                        newSteps[currentStepIndex] = LedStep(currentStepColors)
                                        steps = newSteps
                                    }
                            )
                            if (!isLandscape) {
                                Text(
                                    text = (startChannel + (index * 3)).toString(),
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // Main Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.add(currentStepIndex + 1, LedStep(List(numLeds) { Color.Black }))
                        steps = newSteps
                        currentStepIndex++
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, null)
                        Text("ADD")
                    }
                    Button(onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.add(currentStepIndex + 1, steps[currentStepIndex].copy())
                        steps = newSteps
                        currentStepIndex++
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Text("COPY")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = {
                        val newSteps = steps.toMutableList()
                        val currentStepColors = steps[currentStepIndex].colors.toMutableList()
                        val last = currentStepColors.removeAt(currentStepColors.size - 1)
                        currentStepColors.add(0, last)
                        newSteps[currentStepIndex] = LedStep(currentStepColors)
                        steps = newSteps
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
                        Text("SHIFT R")
                    }
                    FilledTonalButton(onClick = {
                        val newSteps = steps.toMutableList()
                        val currentStepColors = steps[currentStepIndex].colors.toMutableList()
                        val first = currentStepColors.removeAt(0)
                        currentStepColors.add(first)
                        newSteps[currentStepIndex] = LedStep(currentStepColors)
                        steps = newSteps
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        Text("SHIFT L")
                    }
                }

                Button(
                    onClick = {
                        // Implement LanBox Save
                        val dmxData = steps.flatMap { step ->
                            step.colors.flatMap { listOf((it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                        }
                        // For a real app, you'd send a command to store this sequence
                        // For now we just feedback success
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SAVE TO LANBOX")
                }
            }
        }

        Box(modifier = Modifier.padding(padding)) {
            editorContent(Modifier)
        }
    }

    // Fixture Settings Dialog
    if (showFixtureSettings) {
        AlertDialog(
            onDismissRequest = { showFixtureSettings = false },
            containerColor = SurfaceGrey,
            title = { Text("Fixture Settings", color = PrimaryGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var tempNumLeds by remember { mutableStateOf(numLeds.toString()) }
                    var tempStartChannel by remember { mutableStateOf(startChannel.toString()) }
                    
                    OutlinedTextField(
                        value = tempNumLeds,
                        onValueChange = { tempNumLeds = it },
                        label = { Text("Number of LEDs (1-100)") }
                    )
                    OutlinedTextField(
                        value = tempStartChannel,
                        onValueChange = { tempStartChannel = it },
                        label = { Text("DMX Start Channel") }
                    )
                    
                    Button(onClick = {
                        val n = tempNumLeds.toIntOrNull() ?: numLeds
                        val c = tempStartChannel.toIntOrNull() ?: startChannel
                        numLeds = n.coerceIn(1, 100)
                        startChannel = c.coerceIn(1, 512)
                        
                        sharedPrefs.edit {
                            putInt("NUM_LEDS", numLeds)
                            putInt("START_CHANNEL", startChannel)
                        }
                        steps = steps.map { step ->
                            LedStep(List(numLeds) { i -> step.colors.getOrElse(i) { Color.Black } })
                        }
                        showFixtureSettings = false
                    }) { Text("Apply Settings") }
                }
            },
            confirmButton = {}
        )
    }

    // Color Management Dialog
    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            containerColor = SurfaceGrey,
            title = { Text("Manage Color Palette", color = PrimaryGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.height(150.dp)
                    ) {
                        itemsIndexed(colorPalette) { index, color ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        val newPalette = colorPalette.toMutableList()
                                        newPalette.removeAt(index)
                                        colorPalette = newPalette
                                    }
                            )
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            colorPalette = colorPalette + Color(
                                (0..255).random(),
                                (0..255).random(),
                                (0..255).random()
                            )
                        }) {
                            Text("Add Random")
                        }
                        Button(onClick = {
                            if (colorPalette.isNotEmpty()) {
                                colorPalette = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.White, Color.Black)
                            }
                        }) {
                            Text("Reset")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) { Text("Done") }
            }
        )
    }
}

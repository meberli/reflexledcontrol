package com.flowxperts.reflexledcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun CueListEditorScreen(onBack: () -> Unit) {
    var numLeds by remember { mutableIntStateOf(10) }
    var startChannel by remember { mutableIntStateOf(1) }
    var steps by remember { mutableStateOf(listOf(LedStep(List(10) { Color.Black }))) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var selectedColor by remember { mutableStateOf(Color.Red) }

    val currentStep = steps[currentStepIndex]

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
            // Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = numLeds.toString(),
                        onValueChange = {
                            val valInt = it.toIntOrNull() ?: 0
                            if (valInt in 1..100) {
                                numLeds = valInt
                                steps = steps.map { step -> 
                                    LedStep(List(valInt) { i -> step.colors.getOrElse(i) { Color.Black } })
                                }
                            }
                        },
                        label = { Text("LEDs (1-100)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = startChannel.toString(),
                        onValueChange = { startChannel = it.toIntOrNull() ?: 1 },
                        label = { Text("DMX Start", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // Step Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (currentStepIndex > 0) currentStepIndex-- }) {
                    Icon(Icons.Rounded.ChevronLeft, "Prev Step", tint = PrimaryGold)
                }
                Text("STEP ${currentStepIndex + 1} / ${steps.size}", fontWeight = FontWeight.Bold)
                IconButton(onClick = { if (currentStepIndex < steps.size - 1) currentStepIndex++ }) {
                    Icon(Icons.Rounded.ChevronRight, "Next Step", tint = PrimaryGold)
                }
            }

            // Color Picker (Simple Presets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.White, Color.Black)
                presets.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (selectedColor == color) 2.dp else 1.dp, Color.White, CircleShape)
                            .clickable { selectedColor = color }
                    )
                }
            }

            // LED Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(40.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(currentStep.colors) { index, color ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .clickable {
                                    val newSteps = steps.toMutableList()
                                    val newColors = currentStep.colors.toMutableList()
                                    newColors[index] = selectedColor
                                    newSteps[currentStepIndex] = LedStep(newColors)
                                    steps = newSteps
                                }
                        )
                        Text(
                            text = (startChannel + (index * 3)).toString(),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.add(currentStepIndex + 1, LedStep(List(numLeds) { Color.Black }))
                        steps = newSteps
                        currentStepIndex++
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Text("ADD", fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        newSteps.add(currentStepIndex + 1, currentStep.copy())
                        steps = newSteps
                        currentStepIndex++
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null)
                    Text("COPY", fontSize = 10.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        val newColors = currentStep.colors.toMutableList()
                        val last = newColors.removeAt(newColors.size - 1)
                        newColors.add(0, last)
                        newSteps[currentStepIndex] = LedStep(newColors)
                        steps = newSteps
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
                    Text("SHIFT R", fontSize = 10.sp)
                }
                FilledTonalButton(
                    onClick = {
                        val newSteps = steps.toMutableList()
                        val newColors = currentStep.colors.toMutableList()
                        val first = newColors.removeAt(0)
                        newColors.add(first)
                        newSteps[currentStepIndex] = LedStep(newColors)
                        steps = newSteps
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    Text("SHIFT L", fontSize = 10.sp)
                }
            }
        }
    }
}

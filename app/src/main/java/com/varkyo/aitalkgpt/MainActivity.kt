package com.varkyo.aitalkgpt

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request microphone permission
        requestPermission.launch(Manifest.permission.RECORD_AUDIO)

        // TODO: Replace with your OpenAI API key
        // IMPORTANT: In production, store this securely (e.g., in local.properties or use BuildConfig)
        val openAiApiKey = ""

        if (openAiApiKey == "") {
            Toast.makeText(
                this,
                "Please set your OpenAI API key in MainActivity.kt",
                Toast.LENGTH_LONG
            ).show()
        }

        setContent {
            MaterialTheme {
                ConversationScreen(openAiApiKey)
            }
        }
    }
}

@Composable
fun ConversationScreen(openAiApiKey: String) {
    val viewModel: ConversationViewModel = viewModel { ConversationViewModel(openAiApiKey) }

    val state by viewModel.state.collectAsState()
    val userText by viewModel.userText.collectAsState()
    val correctedText by viewModel.correctedText.collectAsState()
    val aiReply by viewModel.aiReply.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "English Learning Call",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Call timer (only show during recording)
        if (state == ConversationState.RECORDING) {
            val callDuration by viewModel.callDurationSeconds.collectAsState()
            CallTimer(callDuration = callDuration)
        }

        // Status indicator
        StatusCard(state = state)

        // Error message
        error?.let { errorMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Error: $errorMessage",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // User's transcribed text (original from Whisper)
        if (userText != null) {
            ConversationCard(
                title = "You said:",
                text = userText!!,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Corrected sentence
        if (correctedText != null) {
            ConversationCard(
                title = "Corrected:",
                text = correctedText!!,
                modifier = Modifier.fillMaxWidth(),
                isAI = false,
                isCorrected = true
            )
        }

        // AI's reply
        if (aiReply != null) {
            ConversationCard(
                title = "Reply:",
                text = aiReply!!,
                modifier = Modifier.fillMaxWidth(),
                isAI = true
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Call button
        CallButton(
            state = state,
            onStartCall = { viewModel.startCall() },
            onStopCall = { viewModel.stopCall() },
            onRetry = { viewModel.retry() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CallTimer(callDuration: Long) {
    val minutes = callDuration / 60
    val seconds = callDuration % 60
    val timeText = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = Color(0xFFF44336),
                        shape = RoundedCornerShape(50)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Call in progress: $timeText",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF44336)
            )
        }
    }
}

@Composable
fun StatusCard(state: ConversationState) {
    val (statusText, statusColor) = when (state) {
        ConversationState.IDLE -> "Ready to call" to Color(0xFF4CAF50)
        ConversationState.RECORDING -> "Listening... (auto-stops on silence)" to Color(0xFFF44336)
        ConversationState.TRANSCRIBING -> "Transcribing your speech..." to Color(0xFFFF9800)
        ConversationState.ASKING -> "Getting AI response..." to Color(0xFF2196F3)
        ConversationState.SPEAKING -> "Generating speech..." to Color(0xFF9C27B0)
        ConversationState.PLAYING -> "Playing response..." to Color(0xFF00BCD4)
        ConversationState.ERROR -> "Error occurred" to Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = statusColor,
                        shape = RoundedCornerShape(50)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ConversationCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    isAI: Boolean = false,
    isCorrected: Boolean = false
) {
    // Determine card color based on type
    val containerColor = when {
        isAI -> MaterialTheme.colorScheme.primaryContainer
        isCorrected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val onContainerColor = when {
        isAI -> MaterialTheme.colorScheme.onPrimaryContainer
        isCorrected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = onContainerColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = onContainerColor
            )
        }
    }
}

@Composable
fun CallButton(
    state: ConversationState,
    onStartCall: () -> Unit,
    onStopCall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        ConversationState.IDLE -> {
            Button(
                onClick = onStartCall,
                modifier = modifier.height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = "Call",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Call",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        ConversationState.RECORDING -> {
            // Show call button with stop option (though auto-stop will handle it)
            Button(
                onClick = onStopCall,
                modifier = modifier.height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "End Call",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "End Call",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        ConversationState.ERROR -> {
            Button(
                onClick = onRetry,
                modifier = modifier.height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Retry Call", style = MaterialTheme.typography.titleMedium)
            }
        }
        else -> {
            // Show disabled button during processing
            Button(
                onClick = { },
                modifier = modifier.height(64.dp),
                enabled = false
            ) {
                Text("Processing...", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

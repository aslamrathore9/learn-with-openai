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
    val aiText by viewModel.aiText.collectAsState()
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
            text = "AI Conversation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
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
        
        // User's transcribed text
        if (userText != null) {
            ConversationCard(
                title = "You said:",
                text = userText!!,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // AI's response
        if (aiText != null) {
            ConversationCard(
                title = "AI replied:",
                text = aiText!!,
                modifier = Modifier.fillMaxWidth(),
                isAI = true
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Record button
        RecordButton(
            state = state,
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecording() },
            onRetry = { viewModel.retry() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StatusCard(state: ConversationState) {
    val (statusText, statusColor) = when (state) {
        ConversationState.IDLE -> "Ready to record" to Color(0xFF4CAF50)
        ConversationState.RECORDING -> "Recording..." to Color(0xFFF44336)
        ConversationState.TRANSCRIBING -> "Transcribing..." to Color(0xFFFF9800)
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
    isAI: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isAI) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isAI)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isAI)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RecordButton(
    state: ConversationState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        ConversationState.IDLE -> {
            Button(
                onClick = onStartRecording,
                modifier = modifier.height(56.dp)
            ) {
                Text("Start Recording", style = MaterialTheme.typography.titleMedium)
            }
        }
        ConversationState.RECORDING -> {
            Button(
                onClick = onStopRecording,
                modifier = modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Recording", style = MaterialTheme.typography.titleMedium)
            }
        }
        ConversationState.ERROR -> {
            Button(
                onClick = onRetry,
                modifier = modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Retry", style = MaterialTheme.typography.titleMedium)
            }
        }
        else -> {
            // Show disabled button during processing
            Button(
                onClick = { },
                modifier = modifier.height(56.dp),
                enabled = false
            ) {
                Text("Processing...", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

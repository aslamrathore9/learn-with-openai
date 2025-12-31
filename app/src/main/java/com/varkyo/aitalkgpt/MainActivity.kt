package com.varkyo.aitalkgpt

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varkyo.aitalkgpt.ui.TopicSelectionScreen
import com.varkyo.aitalkgpt.ui.theme.AiTalkGptTheme
import com.varkyo.aitalkgpt.viewmodel.ConversationViewModel

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

        setContent {
            AiTalkGptTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModel.Factory(context.applicationContext)
    )

    val callState by viewModel.callState.collectAsState()
    val topicTitle by viewModel.currentTopicTitle.collectAsState()

    // Navigate between screens based on CallState
    if (callState is CallState.Idle) {
        TopicSelectionScreen(
            onTopicSelected = { topic -> viewModel.startCall(topic) }
        )
    } else {
        // Unified Conversation Screen
        val hintSuggestion by viewModel.hintSuggestion.collectAsState()
        
        com.varkyo.aitalkgpt.ui.ConversationScreen(
            state = callState,
            topicTitle = topicTitle,
            onEndCall = { viewModel.endCall() },
            onPause = { viewModel.pauseCall() },
            onResume = { viewModel.resumeCall() },
            onContinue = { viewModel.continueConversation() },
            onRequestHint = { viewModel.requestHint() },
            hintSuggestion = hintSuggestion
        )
    }
}



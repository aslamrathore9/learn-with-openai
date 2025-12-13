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

    // Navigate between screens based on CallState
    when (val state = callState) {
        is CallState.Idle -> TopicSelectionScreen(
            onTopicSelected = { topic -> viewModel.startCall(topic) }
        )
        is CallState.Connecting -> CallScreen(isConnecting = true, onCancel = { viewModel.endCall() })
        is CallState.Listening -> ListeningScreen(
            state = state,
            onEndCall = { viewModel.endCall() }
        )
        is CallState.Speaking -> SpeakingScreen(
            state = state,
            onEndCall = { viewModel.endCall() }
        )
        is CallState.Error -> ErrorScreen(
            message = state.message,
            onRetry = { viewModel.startCall() }
        )
    }
}

// ==================== CALL SCREEN ====================
@Composable
fun CallScreen(
    isConnecting: Boolean = false,
    onStartCall: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.varkyo.aitalkgpt.ui.theme.AppBackground), // New BG
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Title
            Text(
                text = "English Learning",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (isConnecting) {
                // Connecting animation
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing circles
                    Box(
                        modifier = Modifier
                            .size(200.dp * scale)
                            .background(
                                color = com.varkyo.aitalkgpt.ui.theme.BrandRed.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .background(
                                color = com.varkyo.aitalkgpt.ui.theme.BrandRed.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = com.varkyo.aitalkgpt.ui.theme.BrandRed,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "Calling",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Calling AI...", 
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    color = com.varkyo.aitalkgpt.ui.theme.BrandRed,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.width(150.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Cancel button
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .width(200.dp)
                        .height(56.dp)
                ) {
                    Text("Cancel", fontSize = 18.sp)
                }
            } else {
                // Call button
                FloatingActionButton(
                    onClick = onStartCall,
                    modifier = Modifier.size(120.dp),
                    containerColor = com.varkyo.aitalkgpt.ui.theme.BrandRed,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "Start Call",
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tap to start conversation",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// ==================== LISTENING SCREEN ====================
@Composable
fun ListeningScreen(
    state: CallState.Listening,
    onEndCall: () -> Unit
) {
    // Simulated Visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.varkyo.aitalkgpt.ui.theme.AppBackground), // New BG
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Listening text
            Text(
                text = "I'm Listening...",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Dynamic Bounce Visualizer
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(120.dp)
            ) {
                 repeat(7) { index ->
                     val duration = 500 + index * 100
                     val heightScale by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(duration, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar$index"
                    )
                    
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight(heightScale) // Always animate
                            .background(com.varkyo.aitalkgpt.ui.theme.BrandRed, CircleShape) // New Color
                    )
                 }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (state.isUserSpeaking) "Detecting Speech..." else "Speak Now",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onEndCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.varkyo.aitalkgpt.ui.theme.SurfaceDark), // Less aggressive for End Call
                 shape = RoundedCornerShape(32.dp)
            ) {
                Icon(Icons.Filled.Clear, "End", modifier = Modifier.size(28.dp), tint = com.varkyo.aitalkgpt.ui.theme.BrandRed)
                Spacer(modifier = Modifier.width(12.dp))
                Text("End Call", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = com.varkyo.aitalkgpt.ui.theme.BrandRed)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ==================== SPEAKING SCREEN ====================
@Composable
fun SpeakingScreen(
    state: CallState.Speaking,
    onEndCall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.varkyo.aitalkgpt.ui.theme.AppBackground), // New BG
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // verticalArrangement = Arrangement.spacedBy(24.dp), 
            modifier = Modifier.padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "AI is speaking...",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // AI Avatar / Animation
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(com.varkyo.aitalkgpt.ui.theme.BrandRed.copy(alpha=0.2f), CircleShape) // Red Glow
                    .padding(20.dp)
            ) {
                Icon(
                     imageVector = Icons.Filled.Face, // Changed to Face for AI
                     contentDescription = null,
                     modifier = Modifier.fillMaxSize(),
                     tint = com.varkyo.aitalkgpt.ui.theme.BrandRed
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // AI Text Streaming
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Take available space
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = com.varkyo.aitalkgpt.ui.theme.SurfaceDark // Dark Surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (state.aiText.isNotEmpty()) {
                        Text(
                            text = state.aiText,
                            fontSize = 22.sp,
                            color = Color.White,
                            lineHeight = 32.sp,
                            textAlign = TextAlign.Start
                        )
                    } else {
                         Text(
                            text = "Thinking...",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha=0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "(Speak to interrupt)",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onEndCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.varkyo.aitalkgpt.ui.theme.SurfaceDark),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(Icons.Filled.Clear, "End", modifier = Modifier.size(28.dp), tint = com.varkyo.aitalkgpt.ui.theme.BrandRed)
                Spacer(modifier = Modifier.width(12.dp))
                Text("End Call", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = com.varkyo.aitalkgpt.ui.theme.BrandRed)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ==================== ERROR SCREEN ====================
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.varkyo.aitalkgpt.ui.theme.AppBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning, // Changed Icon
                contentDescription = "Error",
                modifier = Modifier.size(80.dp),
                tint = com.varkyo.aitalkgpt.ui.theme.BrandRed
            )

            Text(
                text = "Connection Error",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = com.varkyo.aitalkgpt.ui.theme.SurfaceDark
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.varkyo.aitalkgpt.ui.theme.BrandRed
                ),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Retry",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

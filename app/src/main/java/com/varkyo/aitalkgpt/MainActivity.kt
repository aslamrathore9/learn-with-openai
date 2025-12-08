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
import com.varkyo.aitalkgpt.ui.theme.AiTalkGptTheme

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
        is CallState.Idle -> CallScreen(onStartCall = { viewModel.startCall() })
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E88E5),
                        Color(0xFF1565C0)
                    )
                )
            ),
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
                                color = Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = Color.White,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "Calling",
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF1565C0)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Connecting...",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(40.dp)
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
                    containerColor = Color.White,
                    contentColor = Color(0xFF4CAF50)
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
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = if (state.isUserSpeaking) 1f else 0.9f,
        targetValue = if (state.isUserSpeaking) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4CAF50),
                        Color(0xFF388E3C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Listening text
            Text(
                text = "Listening...",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Animated microphone icon
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(micScale)
                    .background(
                        color = Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Microphone",
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // User transcript (if available)
            if (state.userTranscript.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "You said:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.userTranscript,
                            fontSize = 18.sp,
                            color = Color.White,
                            lineHeight = 24.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Speak naturally...",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // End Call button
            Button(
                onClick = {
                    android.util.Log.d("MainActivity", "🔴 End Call button clicked (Listening)")
                    onEndCall()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                ),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "End Call",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "End Call",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2196F3),
                        Color(0xFF1976D2)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Speaking text
            Text(
                text = "AI is speaking...",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Animated sound waves
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    val delay = index * 100
                    val animatedScale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 800,
                                delayMillis = delay,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "wave$index"
                    )

                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(60.dp * animatedScale)
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // AI text display (streaming)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 150.dp, max = 300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.aiText.isNotEmpty()) {
                        Text(
                            text = state.aiText,
                            fontSize = 20.sp,
                            color = Color.White,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // End Call button
            Button(
                onClick = {
                    android.util.Log.d("MainActivity", "🔴 End Call button clicked (Speaking)")
                    onEndCall()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                ),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "End Call",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "End Call",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF44336),
                        Color(0xFFD32F2F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "Error",
                modifier = Modifier.size(80.dp),
                tint = Color.White
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
                    containerColor = Color.White.copy(alpha = 0.2f)
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
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Retry",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

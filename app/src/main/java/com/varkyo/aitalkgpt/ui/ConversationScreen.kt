package com.varkyo.aitalkgpt.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varkyo.aitalkgpt.CallState
import com.varkyo.aitalkgpt.R
import com.varkyo.aitalkgpt.ui.theme.AppBackground
import com.varkyo.aitalkgpt.ui.theme.BrandRed
import com.varkyo.aitalkgpt.ui.theme.SurfaceDark
import com.airbnb.lottie.compose.*
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ConversationScreen(
    state: CallState,
    topicTitle: String, // Dynamic Title
    onEndCall: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onContinue: () -> Unit = {} // Hint/Continue
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Preload Lottie Compositions
    val speakingComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lyra_bounce_speaking))
    val thinkingComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lyra_horizontal_loading))

    // Sound Effect Trigger
    val previousState = remember { mutableStateOf<CallState?>(null) }

    LaunchedEffect(state) {
        val prev = previousState.value

        // Play AI Sound when switching to Thinking (User finished speaking)
        if (prev !is CallState.Thinking && state is CallState.Thinking) {
            kotlinx.coroutines.delay(400) // Ensure visual switch happens first
            com.varkyo.aitalkgpt.utils.SoundManager.playAiTurnSound()
        }
        // Play User Sound when switching to Listening (AI finished speaking)
        else if (prev !is CallState.Listening && state is CallState.Listening) {
            kotlinx.coroutines.delay(400) // Ensure visual switch happens first
            com.varkyo.aitalkgpt.utils.SoundManager.playUserTurnSound()
        }
        previousState.value = state
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (state is CallState.Initializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black), // Or use AppBackground
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BrandRed,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            // Normal UI
            Image(
                painter = painterResource(id = R.drawable.wa_call_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEndCall) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = topicTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { /* Settings? */ }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }

            // Avatars & Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, bottom = 120.dp), // Clear TopBar and ControlBar area
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // --- TOP HALF: AI AVATAR & USER AVATAR ---

                // 1. AI Avatar
                val isAiSpeaking = state is CallState.Speaking
                // Thinking State: Explicit CallState.Thinking, OR Listening but "Thinking" (legacy)
                val isThinking = state is CallState.Thinking
                val isPaused = state is CallState.Paused

                Box(contentAlignment = Alignment.Center) {
                    // Lottie Link for AI
                    if (isAiSpeaking && !isPaused) {
                        LottieAnimation(
                            composition = speakingComposition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.size(135.dp) // Subtle border effect (120dp avatar + buffer)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(115.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(BrandRed, Color.Black)
                                )
                            )
                            .border(
                                width = if (isAiSpeaking && !isPaused) 3.dp else 0.dp,
                                color = if (isAiSpeaking && !isPaused) Color.White else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isThinking) {
                            LottieAnimation(
                                composition = thinkingComposition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier.width(110.dp).height(100.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.lyra_speaking),
                                contentDescription = "AI Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxSize()
                                    .scale(if (isAiSpeaking && !isPaused) 1.1f else 1f),
                                colorFilter = if(isPaused) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null
                            )
                        }
                    }
                }

//            Spacer(modifier = Modifier.height(1.dp))

                Text(
                    text = when {
                        isThinking -> "Lyra is thinking"
                        isAiSpeaking && !isPaused -> "Lyra Speaking"
                        state is CallState.Connecting -> "Connecting..."
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 2. User Avatar (Stacked below AI)
                val isUserActive = state is CallState.Listening

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        // Lottie Link for User
                        if (isUserActive && !isPaused) {
                            LottieAnimation(
                                composition = speakingComposition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier.size(115.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .border(
                                    width = if (isUserActive && !isPaused) 4.dp else 0.dp,
                                    color = if (isUserActive && !isPaused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.male),
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .fillMaxSize() // Use fillMaxSize for crop
                                    .scale(if (isUserActive && !isPaused) 1.1f else 1f),
                                contentScale = ContentScale.Crop, // Crop to circle
                                colorFilter = if(isPaused) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isUserActive && !isPaused) "Listening..." else "Wait for your trun",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }


                // --- BOTTOM HALF: ALERTS & CAPTIONS ---

                // Spacer fills the gap between User and Caption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center // Alerts in the middle of this gap
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Alerts (Voice not detected / Paused)
                        if (state is CallState.Error) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2E3240).copy(alpha=0.9f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE91E63), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                                    .clickable { onContinue() }
                            ) {
                                Text(
                                    "Voice not detected, tap to retry",
                                    color = Color(0xFFE91E63),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (state is CallState.Paused) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2E3240).copy(alpha=0.9f), RoundedCornerShape(24.dp))
                                    .border(1.dp, Color(0xFF3E4250), RoundedCornerShape(24.dp))
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                    .clickable { onResume() }
                            ) {
                                Text("Press to continue", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // 4. Caption (Fixed at Bottom of Column, above Controls)
                if (state is CallState.Speaking && state.aiText.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A2E39)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    ) {
                        Column {
                            // Header Box with distinct background
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF07191A), // Start Color
                                                Color(0xFF022928)  // End Color (Darker Blue)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cc),
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "ARYA Captions",
                                        color = Color(0xFFFFC107),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF566373),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(16.dp)
                                        .clickable { /* Close logic */ }
                                )
                            }

                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF2A2E39))

                            Box(
                                modifier = Modifier
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF07191A), // Start Color
                                                Color(0xFF022928)  // End Color (Darker Blue)
                                            )
                                        )
                                    )
                                    .padding(16.dp)
                                    .heightIn(max = 100.dp) // Adjusted for padding (16*2) + ~3 lines (20*3)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = state.aiText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Control Bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hint
                ControlButton(
                    icon = Icons.Default.Star, // Placeholder for Sparkles
                    label = "Hint",
                    color = Color(0xFF1E222B),
                    iconTint = Color.White
                )

                // Caption
                ControlButton(
                    icon = Icons.Default.Info, // Placeholder for CC
                    label = "Caption",
                    color = Color.White, // Active state example
                    iconTint = Color.Black
                )

                // Pause / Resume
                val isPaused = state is CallState.Paused
                ControlButton(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Menu, // Using Menu as placeholder for pause lines if needed, or stick to standard
                    label = if (isPaused) "Resume" else "Pause",
                    color = Color.White,
                    iconTint = Color.Black,
                    onClick = { if (isPaused) onResume() else onPause() }
                )

                // Continue
                ControlButton(
                    icon = Icons.Default.Send,
                    label = "Continue",
                    color = Color(0xFF1E222B),
                    iconTint = Color.White,
                    onClick = onContinue
                )

                // End
                ControlButton(
                    icon = Icons.Default.Close,
                    label = "End",
                    color = BrandRed,
                    iconTint = Color.White,
                    onClick = onEndCall
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    iconTint: Color,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, CircleShape)
                .border(
                    width = if(color == Color(0xFF1E222B)) 1.dp else 0.dp,
                    color = Color(0xFF2A2E39),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
fun RippleAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(120.dp) // Base size matches avatar
            .scale(scale)
            .border(2.dp, color.copy(alpha = alpha), CircleShape)
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Idle() { // Connecting State
    ConversationScreen(
        state = CallState.Connecting,
        topicTitle = "Connecting...",
        onEndCall = {},
        onPause = {},
        onResume = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Speaking() {
    ConversationScreen(
        state = CallState.Speaking(aiText = "Hello, I am ready to help you learn English! Start by telling me about your day."),
        topicTitle = "Daily Routine",
        onEndCall = {},
        onPause = {},
        onResume = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Listening() {
    ConversationScreen(
        state = CallState.Listening(isUserSpeaking = true, userTranscript = "I want to learn..."),
        topicTitle = "Improve Vocabulary",
        onEndCall = {},
        onPause = {},
        onResume = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Paused() {
    ConversationScreen(
        state = CallState.Paused,
        topicTitle = "Paused Topic",
        onEndCall = {},
        onPause = {},
        onResume = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Error() {
    ConversationScreen(
        state = CallState.Error("Voice not detected"),
        topicTitle = "Error State",
        onEndCall = {},
        onPause = {},
        onResume = {}
    )
}

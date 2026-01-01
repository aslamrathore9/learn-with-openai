package com.varkyo.aitalkgpt.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varkyo.aitalkgpt.CallState
import com.varkyo.aitalkgpt.R
import com.varkyo.aitalkgpt.ui.theme.BrandRed
import com.airbnb.lottie.compose.*
import androidx.compose.ui.tooling.preview.Preview
import com.varkyo.aitalkgpt.ui.theme.NunitoFontFamily
import com.varkyo.aitalkgpt.ui.theme.PictonBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: CallState,
    topicTitle: String, // Dynamic Title
    onEndCall: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onContinue: () -> Unit = {}, // Hint/Continue
    onRequestHint: () -> Unit = {},
    hintSuggestion: String? = null,
    isHintVisible: Boolean = false
) {
    // Bottom Sheet State
    var showExitBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Caption State
    var isCaptionEnabled by remember { mutableStateOf(true) }
    
    // Pause State - accessible by both Column and Row
    val isPaused = state is CallState.Paused
    val context = LocalContext.current
    
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
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NunitoFontFamily
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

                val avatarSize by animateDpAsState(
                    targetValue = if ((isAiSpeaking || isThinking) && !isPaused) 100.dp else 80.dp,
                    label = "avatarSize",
                     animationSpec = tween(durationMillis = 250, easing = LinearEasing)
                )

                Box(contentAlignment = Alignment.Center) {
                    // Native Ripple Animation for AI
                    if (isAiSpeaking && !isPaused) {
                        RippleAnimation(color = Color.White, size = avatarSize)
                    }

                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(if (isPaused) Color.Gray else PictonBlue)
                            .border(
                                width = if (isAiSpeaking && !isPaused) 3.dp else 0.dp,
                                color = if (isAiSpeaking && !isPaused) Color.White else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isThinking && !isPaused) {
                            // Show Lottie animation when AI is thinking
                            LottieAnimation(
                                composition = thinkingComposition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier
                                    .scale(1.2f)
                                    .fillMaxSize()
                                    //.size(avatarSize * 1.8f)
                            )
                        } else {
                            // Show image based on state
                            val avatarImage = when {
                                isAiSpeaking -> R.drawable.lyra_speaking
                                else -> R.drawable.lyra_wait // Waiting for user
                            }
                            
                            Image(
                                painter = painterResource(id = avatarImage),
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

                val spacerHeight by animateDpAsState(
                    targetValue = if ((isAiSpeaking || isThinking) && !isPaused) 10.dp else 2.dp,
                    label = "spacerHeight"
                )
                Spacer(modifier = Modifier.height(spacerHeight))

                Text(
                    text = when {
                        isThinking -> "Lyra is thinking"
                        isAiSpeaking && !isPaused -> "Lyra Speaking"
                        state is CallState.Connecting -> "Connecting..."
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontFamily = NunitoFontFamily
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 2. User Avatar (Stacked below AI)
                val isUserActive = state is CallState.Listening

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val userAvatarSize by animateDpAsState(
                        targetValue = if (isUserActive && !isPaused) 100.dp else 80.dp,
                        label = "userAvatarSize",
                        animationSpec = tween(durationMillis = 250, easing = LinearEasing)
                    )

                    Box(contentAlignment = Alignment.Center) {
                        // Native Ripple for User
                        if (isUserActive && !isPaused) {
                            RippleAnimation(color = Color.White, size = userAvatarSize)
                        }

                        Box(
                            modifier = Modifier
                                .size(userAvatarSize)
                                .clip(CircleShape)
                                .background(if (isPaused) Color.Gray else PictonBlue)
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
                                    .fillMaxSize(),
                                contentScale = ContentScale.Fit, // Crop to circle
                                colorFilter = if(isPaused) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null
                            )
                        }
                    }

                    val userSpacerHeight by animateDpAsState(
                        targetValue = if (isUserActive && !isPaused) 10.dp else 2.dp,
                        label = "userSpacerHeight"
                    )
                    Spacer(modifier = Modifier.height(userSpacerHeight))

                    Text(
                        text = if (isUserActive && !isPaused) "Listening..." else "Wait for your trun",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = NunitoFontFamily
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
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = NunitoFontFamily
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
                                Text("Press to continue", color = Color.White, fontSize = 16.sp, fontFamily = NunitoFontFamily)
                            }
                        }
                    }
                }

                // 4. Caption or Hint (Fixed at Bottom of Column, above Controls)
                
                // Show Hint if visible and available (Priority over Caption)
                if (isHintVisible && hintSuggestion != null) {
                     Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp),
                        border = null, // No outer border
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF252756), // Dark purple
                                            Color(0xFF01091A),  // Dark blue

                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(top = 10.dp, bottom = 2.dp,start = 16.dp, end = 16.dp)
                        ) {
                            // Header with close button
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFCC831D), // Orange/gold star
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        "Suggestion for reply",
                                        color = Color(0xFFCC831D), // Orange/gold text
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NunitoFontFamily
                                    )
                                }
                                
                                // Close button
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF0F1525),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(20.dp)
                                        .clickable { onRequestHint() } // Toggle hint visibility
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Content box with border/elevation
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .wrapContentWidth()
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF4A4D7E).copy(alpha = 0.6f), // Subtle purple border
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF252756), // Dark purple
                                                Color(0xFF01091A),  // Dark blue
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = hintSuggestion,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 28.sp,
                                    fontFamily = NunitoFontFamily
                                )
                            }
                        }
                    }
                } else if (isCaptionEnabled && state is CallState.Speaking && state.aiText.isNotEmpty()) {
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
                                        brush = Brush.verticalGradient(
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
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NunitoFontFamily
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
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF07191A), // Start Color
                                                Color(0xFF022928)  // End Color (Darker Blue)
                                            )
                                        )
                                    )
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .heightIn(max = 100.dp) // Adjusted for padding (16*2) + ~3 lines (20*3)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = state.aiText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = NunitoFontFamily
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
                val isListenersTurn = state is CallState.Listening
                
                ControlButton(
                    icon = Icons.Default.Star, 
                    label = "Hint",
                    color = if (isHintVisible) Color.White else Color(0xFF1E222B),
                    iconTint = if (isHintVisible) Color.Black else Color.White,
                    onClick = { 
                        if (!isListenersTurn) {
                            Toast.makeText(context, "Wait for your turn", Toast.LENGTH_SHORT).show()
                        } else if (!isPaused) {
                            onRequestHint()
                        }
                    }
                )

                // Caption
                ControlButton(
                    icon = Icons.Default.Info, // Fallback
                    label = "Caption",
                    color = if (isCaptionEnabled) Color.White else Color(0xFF1E222B),
                    iconTint = if (isCaptionEnabled) Color.Black else Color.White,
                    onClick = { isCaptionEnabled = !isCaptionEnabled },
                    drawableRes = R.drawable.cc
                )


                // Pause / Resume
                ControlButton(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Menu,
                    label = if (isPaused) "Resume" else "Pause",
                    color = if (isPaused) Color.White else Color(0xFF1E222B),
                    iconTint = if (isPaused)  Color(0xFF1E222B) else Color.White,
                    onClick = { if (isPaused) onResume() else onPause() },
                    isPauseButton = !isPaused
                )



                // Continue
                ControlButton(
                    icon = Icons.Default.Send,
                    label = "Continue",
                    color = Color(0xFF1E222B),
                    iconTint = Color.White,
                    onClick = {
                        if (isPaused) {
                            Toast.makeText(context, "Resume call first", Toast.LENGTH_SHORT).show()
                        } else {
                            onContinue()
                        }
                    }
                )

                // End
                ControlButton(
                    icon = Icons.Default.Close,
                    label = "End",
                    color = BrandRed,
                    iconTint = Color.White,
                    onClick = { showExitBottomSheet = true }
                )
            }
        }
    }
    
    // Exit Confirmation Bottom Sheet
    if (showExitBottomSheet) {
        ExitConfirmationBottomSheet(
            onDismiss = { showExitBottomSheet = false },
            onContinue = { showExitBottomSheet = false },
            onExit = {
                showExitBottomSheet = false
                onEndCall()
            },
            sheetState = sheetState
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitConfirmationBottomSheet(
    onDismiss: () -> Unit = {},
    onContinue: () -> Unit = {},
    onExit: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1D26),
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Robot Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lyra_speaking),
                    contentDescription = "AI Robot",
                    modifier = Modifier,
                    contentScale = ContentScale.Fit
                )
            }
            
            Spacer(modifier = Modifier.height(15.dp))
            
            // Title
            Text(
                text = "Don't leave just yet!",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = NunitoFontFamily
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Description
            Text(
                text = "Hey, the call barely began! Say a few more lines\nand see how it goes?",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                fontFamily = NunitoFontFamily
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Continue Button
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Continue",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NunitoFontFamily
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Exit Button
            TextButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Exit",
                    color = Color(0xFF6366F1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NunitoFontFamily
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}


@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    iconTint: Color,
    onClick: () -> Unit = {},
    isPauseButton: Boolean = false,
    drawableRes: Int? = null // Optional drawable resource
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
            if (isPauseButton) {
                // Custom Pause Icon - Two vertical bars
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(14.dp)
                            .background(iconTint, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(14.dp)
                            .background(iconTint, RoundedCornerShape(2.dp))
                    )
                }
            } else if (drawableRes != null) {
                // Use drawable resource if provided
                Icon(
                    painter = painterResource(id = drawableRes),
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                // Use ImageVector as fallback
                Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.Gray, fontSize = 11.sp, fontFamily = NunitoFontFamily)
    }
}

@Composable
fun RippleAnimation(color: Color, size: androidx.compose.ui.unit.Dp = 115.dp) {
    Box(contentAlignment = Alignment.Center) {
        val waves = listOf(0, 750) // Reduced to 2 layers
        waves.forEach { delay ->
            val infiniteTransition = rememberInfiniteTransition(label = "ripple_\$delay")

            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.35f, // Reduced expansion width
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing), // Slower, smoother
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "scale"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "alpha"
            )

            Box(
                modifier = Modifier
                    .size(size) // Match Avatar Size
                    .scale(scale)
                    .background(color.copy(alpha = alpha), CircleShape)
            )
        }
    }
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
        onResume = {},
        isHintVisible = false
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Thinking() {
    ConversationScreen(
        state = CallState.Thinking,
        topicTitle = "Daily Routine",
        onEndCall = {},
        onPause = {},
        onResume = {},
        isHintVisible = false
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
        onResume = {},
        isHintVisible = false
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_Paused() {
    ConversationScreen(
        state = CallState.Paused(previousState = CallState.Listening()),
        topicTitle = "Paused Topic",
        onEndCall = {},
        onPause = {},
        onResume = {},
        isHintVisible = false
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
        onResume = {},
        isHintVisible = false
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConversationScreen_HintSuggestion() {
    ConversationScreen(
        state = CallState.Listening(isUserSpeaking = false, userTranscript = ""),
        topicTitle = "Daily Routine",
        onEndCall = {},
        onPause = {},
        onResume = {},
        onContinue = {},
        onRequestHint = {},
        hintSuggestion = "what hobbies do you enjoy?",
        isHintVisible = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PreviewExitConfirmationBottomSheet() {
    ExitConfirmationBottomSheet()
}

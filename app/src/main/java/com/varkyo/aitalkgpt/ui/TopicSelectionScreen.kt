package com.varkyo.aitalkgpt.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.launch
import com.varkyo.aitalkgpt.R
import com.varkyo.aitalkgpt.ui.theme.AppBackground
import com.varkyo.aitalkgpt.ui.theme.BrandRed
import com.varkyo.aitalkgpt.ui.theme.SurfaceDark
import com.varkyo.aitalkgpt.ui.theme.SurfaceDark2

val NunitoLight = FontFamily(Font(R.font.nunito_light))

data class Topic(
    val id: String,
    val title: String,
    val subTitle: String = "",
    val icon: ImageVector,
    val color: Color,
    val category: String, // "General" or "Interview"
    val difficulty: String = "Easy",
    val lastChatDate: String? = null,
    val practiceCount: Int = 0,
    val lottieRes: Int? = null // Optional Lottie resource
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TopicSelectionScreen(
    onTopicSelected: (Topic) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val categories = remember { listOf("General", "Interview") }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { categories.size })
    var showBottomSheet by remember { mutableStateOf<Topic?>(null) }

    val topics = remember { getTopics() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground) // New Background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopBar()

            // Tabs
            CategoryTabs(
                selectedCategory = categories[pagerState.currentPage],
                onCategorySelected = { category ->
                    val index = categories.indexOf(category)
                    if (index >= 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                            )
                        }
                    }
                }
            )

            // HorizontalPager implementation
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondBoundsPageCount = 1,
                userScrollEnabled = false
            ) { page ->
                val currentCategory = categories[page]
                val filteredTopics = topics.filter { it.category == currentCategory }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTopics, key = { it.id }) { topic ->
                        TopicCard(
                            topic = topic,
                            onClick = { showBottomSheet = topic }
                        )
                    }
                }
            }
        }

        // Bottom Sheet
        if (showBottomSheet != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = null },
                dragHandle = null,
                containerColor = SurfaceDark, // New Surface
                sheetState = sheetState
            ) {
                CallConfirmationSheet(
                    topic = showBottomSheet!!,
                    onStartCall = {
                        onTopicSelected(showBottomSheet!!)
                        showBottomSheet = null
                    },
                    onDismiss = { showBottomSheet = null }
                )
            }
        }
    }
}

@Composable
fun TopBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Header Row: Avatar, Pro, Rank, Streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF6C63FF), CircleShape) // Placeholder Color
                    .border(2.dp, Color(0xFF3B3E58), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder Image/Icon
                Image(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                // Small menu icon overlay? (As per reference image)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(16.dp)
                        .background(SurfaceDark, CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Get Pro Button
            Button(
                onClick = { /* TODO */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D3A)),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFFFFC107)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Get Pro",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Rank Badge
            Box(
                modifier = Modifier
                    .background(Color(0xFF2A2D3A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Face, // Leaderboard placeholder
                    contentDescription = null,
                    tint = Color(0xFF4CAF50), // Greenish for rank
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Streak Badge
            Box(
                modifier = Modifier
                    .background(Color(0xFF2A2D3A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "0",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = NunitoLight
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bot Icon


                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(32.dp)
                        .background(BrandRed, CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lyra_speaking),
                        contentDescription = "Bot Avatar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                }

               /* Box(
                    modifier = Modifier
                        .background(
                            color = BrandRed,
                            shape = Roun
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lyra_speaking),
                        contentDescription = "Bot Avatar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                }
*/
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Lyra AI",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoLight
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "History",
                color = Color.Gray,
                fontSize = 14.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable { /* TODO */ },
                fontFamily = NunitoLight
            )
        }
    }
}

@Composable
fun CategoryTabs(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        TabItem(
            text = "General",
            selected = selectedCategory == "General",
            onClick = { onCategorySelected("General") },
            modifier = Modifier.weight(1f)
        )
        TabItem(
            text = "Interview",
            selected = selectedCategory == "Interview",
            onClick = { onCategorySelected("Interview") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(if (selected) BrandRed.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = text,
                color = if (selected) BrandRed else Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoLight
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(BrandRed, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp) // Height less than selected tab (4.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TopicCard(
    topic: Topic,
    onClick: () -> Unit
) {
    // Outer Box for Floating Elements (Crown)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp) // Space for the floating crown
            .clickable(onClick = onClick)
    ) {
        // Main Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, Color(0xFF3D4252)), // Subtle border
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Square aspect ratio
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 5.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row: Battery/Level only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start, // Changed to Start since Practice count is moved
                    ) {
                        // Battery/Level

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
                            modifier = Modifier.weight(3f)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.lyra_level),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(width = 29.dp, height = 10.dp)
                            )

                            Text(
                                text = topic.difficulty,
                                color = Color.Gray,
                                fontSize = 7.sp,
                                fontFamily = NunitoLight,
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .offset(y = (-2).dp), //  removes tiny visual gap
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(
                                        includeFontPadding = false
                                    ),
                                    lineHeight = 10.sp
                                )
                            )
                        }

                        Box(modifier = Modifier.weight(7f))

                    }

                    // Center Icon / Illustration / Lottie
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (topic.lottieRes != null) {
                            val composition by rememberLottieComposition(
                                LottieCompositionSpec.RawRes(
                                    topic.lottieRes
                                )
                            )
                            if (composition != null) {
                                LottieAnimation(
                                    composition = composition,
                                    iterations = LottieConstants.IterateForever,
                                    modifier = Modifier.size(60.dp)
                                )
                            } else {
                                // Loading or Error (Preview) -> Fallback to Icon
                                Icon(
                                    imageVector = topic.icon,
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        } else {
                            // Fallback to Icon
                            Icon(
                                imageVector = topic.icon,
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    // Bottom: Title & Last Chat
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = topic.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            fontFamily = NunitoLight
                        )


                        Text(
                            text = "Last chat: ${topic.lastChatDate ?: "Never"}",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = NunitoLight
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                }

                // Practice Count Badge (Top Right)
                if (topic.practiceCount >= 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f), // Light transparent green
                                shape = RoundedCornerShape(bottomStart = 12.dp)
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Practiced ${topic.practiceCount} times",
                            color = Color(0xFF4CAF50), // Green
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = NunitoLight
                        )
                    }
                }
            }
        }

        // Floating Crown Icon
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-12).dp) // Move up half its size
                .size(24.dp)
                .background(SurfaceDark2, CircleShape)
                .border(1.dp, Color.Gray, CircleShape)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.pro_crown), // Crown-like
                contentDescription = null,
                tint = Color(0xFFD4AF37), // Gold
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CallConfirmationSheet(
    topic: Topic,
    onStartCall: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        // Header: Title + Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select a method to practice",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = NunitoLight
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Bar (Static for design)
        LinearProgressIndicator(
            progress = { 0.7f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF6C63FF), // Purple/Blue accent from avatar ring? Or Grey? Let's use a muted accent.
            trackColor = Color.Gray.copy(alpha = 0.3f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "20:00 minutes remaining of free trial",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = NunitoLight
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Call Button
            Button(
                onClick = onStartCall,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call", fontSize = 16.sp, color = Color.White, fontFamily = NunitoLight)
            }

            // Chat Button
            Button(
                onClick = { /* TODO: Chat action */ },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, tint = Color.White) // Using Email as chat placeholder
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chat", fontSize = 16.sp, color = Color.White, fontFamily = NunitoLight)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun getTopics(): List<Topic> {
    //val lyra_talk_about_workplace = R.raw.lyra_talk_about_workplace
    // val lyra_family_relationship = R.raw.lyra_family_relationship
    return listOf(
        Topic(
            id = "talk_about_anything",
            title = "Talk about anything",
            icon = Icons.Default.Face,
            color = Color(0xFFE91E63),
            category = "General",
            practiceCount = 9,
            lastChatDate = "08 Dec 2025",
            lottieRes = R.raw.lyra_talk_about_anything
        ),
        Topic(
            id = "daily_routine",
            title = "Daily routine",
            icon = Icons.Default.DateRange,
            color = Color(0xFF2196F3),
            category = "General",
            practiceCount = 2,
            lastChatDate = "09 Dec 2025",
            lottieRes = R.raw.lyra_daily_routine
        ),
        Topic(
            id = "childhood_memory",
            title = "Talk about your childhood memory",
            icon = Icons.Default.Person,
            color = Color(0xFF9C27B0),
            category = "General",
            practiceCount = 0,
            lottieRes = R.raw.lyra_childhood_memory
        ),
        Topic(
            id = "seasons_weather",
            title = "Seasons and Weather",
            icon = Icons.Default.Edit,
            color = Color(0xFFFF9800),
            category = "General",
            practiceCount = 1,
            lastChatDate = "29 Nov 2025",
            lottieRes = R.raw.lyra_weather
        ),
        Topic(
            id = "family_relationship",
            title = "Family and Relationship",
            icon = Icons.Default.Person,
            color = Color(0xFF9C27B0),
            category = "General",
            practiceCount = 0,
            lottieRes = R.raw.lyra_family_relationship
        ),

        Topic(
            id = "hobbies_interests",
            title = "Hobbies and Interests",
            icon = Icons.Default.Edit,
            color = Color(0xFFFF9800),
            category = "General",
            practiceCount = 1,
            lastChatDate = "29 Nov 2025",
            lottieRes = R.raw.lyrahobbies_interest
        ),
        Topic(
            id = "talk_about_your_workplace",
            title = "Talk about your workplace",
            icon = Icons.Default.Person,
            color = Color(0xFF9C27B0),
            category = "General",
            practiceCount = 0,
            lottieRes = R.raw.lyra_workplace
        ),
        Topic(
            id = "improve_vocabulary",
            title = "Let's Improve vocabulary",
            icon = Icons.Default.Edit,
            color = Color(0xFFFF9800),
            category = "General",
            practiceCount = 1,
            lastChatDate = "29 Nov 2025",
            lottieRes = R.raw.lyra_vocabulary
        ),
        // Interviews
        Topic(
            id = "intro_practice",
            title = "Introduction dene ki practice kare",
            icon = Icons.Default.AccountBox,
            color = Color(0xFF00BCD4),
            category = "Interview",
            difficulty = "Medium",
            lottieRes = R.raw.lyra_childhood_memory
        ),
        Topic(
            id = "career_plans",
            title = "Apne career plans share kare",
            icon = Icons.Default.Build,
            color = Color(0xFF673AB7),
            category = "Interview",
            difficulty = "Medium",
            lottieRes = R.raw.lyra_childhood_memory
        ),
        Topic(
            id = "govt_interview",
            title = "UPSC interview ki practice kare",
            icon = Icons.Default.AccountCircle,
            color = Color(0xFF795548),
            category = "Interview",
            difficulty = "Hard",
            lottieRes = R.raw.lyra_childhood_memory
        ),
        Topic(
            id = "job_interview",
            title = "Job interview ki practice kare",
            icon = Icons.Default.Email,
            color = Color(0xFF607D8B),
            category = "Interview",
            difficulty = "Medium",
            lottieRes = R.raw.lyra_childhood_memory
        )
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun TopicSelectionScreenPreview() {
    MaterialTheme {
        TopicSelectionScreen(onTopicSelected = {})
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun CallConfirmationSheetPreview() {
    val sampleTopic = Topic(
        id = "sample_topic",
        title = "Sample Topic",
        icon = Icons.Default.Face,
        color = Color(0xFFE91E63),
        category = "General",
        practiceCount = 5,
        lastChatDate = "10 Dec 2025"
    )
    MaterialTheme {
        Box(modifier = Modifier.background(SurfaceDark)) {
            CallConfirmationSheet(
                topic = sampleTopic,
                onStartCall = {},
                onDismiss = {}
            )
        }
    }
}

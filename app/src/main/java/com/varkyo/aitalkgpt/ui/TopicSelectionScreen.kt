package com.varkyo.aitalkgpt.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Topic(
    val id: String,
    val title: String,
    val subTitle: String = "",
    val icon: ImageVector,
    val color: Color,
    val category: String, // "General" or "Interview"
    val difficulty: String = "Easy",
    val lastChatDate: String? = null,
    val practiceCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSelectionScreen(
    onTopicSelected: (Topic) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("General") }
    var showBottomSheet by remember { mutableStateOf<Topic?>(null) }
    
    val topics = remember { getTopics() }
    val filteredTopics = topics.filter { it.category == selectedCategory }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Dark background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopBar()
            
            // Tabs
            CategoryTabs(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )
            
            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredTopics) { topic ->
                    TopicCard(
                        topic = topic,
                        onClick = { showBottomSheet = topic }
                    )
                }
            }
        }
        
        // Bottom Sheet
        if (showBottomSheet != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = null },
                containerColor = Color(0xFF1E1E1E),
                sheetState = sheetState
            ) {
                CallConfirmationSheet(
                    topic = showBottomSheet!!,
                    onStartCall = {
                        onTopicSelected(showBottomSheet!!)
                        showBottomSheet = null
                    }
                )
            }
        }
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF6200EA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Title
        Column {
            Text(
                "ARYA AI",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "History",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.clickable { /* TODO */ }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Pro Button
        Button(
            onClick = { /* TODO */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
             modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Get Pro", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF7C4DFF) else Color.Gray,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        if (selected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.dp)
                    .background(Color(0xFF7C4DFF))
            )
        }
    }
}

@Composable
fun TopicCard(
    topic: Topic,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                 Text(
                     text = topic.difficulty,
                     color = Color.Gray,
                     fontSize = 10.sp
                 )
                 if (topic.practiceCount > 0) {
                     Text(
                         text = "Practiced ${topic.practiceCount} times",
                         color = Color(0xFF4CAF50),
                         fontSize = 10.sp
                     )
                 }
            }
            
            // Icon
            Icon(
                imageVector = topic.icon,
                contentDescription = null,
                tint = topic.color,
                modifier = Modifier.size(48.dp)
            )
            
            // Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = topic.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
                if (topic.lastChatDate != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                         text = "Last: ${topic.lastChatDate}",
                         color = Color.Gray,
                         fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CallConfirmationSheet(
    topic: Topic,
    onStartCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ready to practice?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TopicCard(topic = topic, onClick = {}) // Preview selected topic
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onStartCall,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Phone, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun getTopics(): List<Topic> {
    return listOf(
        Topic(
            id = "talk_about_anything",
            title = "Talk about anything",
            icon = Icons.Default.Face,
            color = Color(0xFFE91E63),
            category = "General",
            practiceCount = 9,
            lastChatDate = "08 Dec 2025"
        ),
        Topic(
            id = "daily_routine",
            title = "Daily routine",
            icon = Icons.Default.DateRange,
            color = Color(0xFF2196F3),
            category = "General",
            practiceCount = 2,
            lastChatDate = "09 Dec 2025"
        ),
         Topic(
            id = "improve_vocabulary",
            title = "Let's Improve vocabulary",
            icon = Icons.Default.Edit,
            color = Color(0xFFFF9800),
            category = "General",
            practiceCount = 1,
            lastChatDate = "29 Nov 2025"
        ),
        Topic(
            id = "childhood_memory",
            title = "Talk about your childhood memory",
            icon = Icons.Default.Person,
            color = Color(0xFF9C27B0),
            category = "General",
            practiceCount = 0
        ),
        // Interviews
        Topic(
            id = "intro_practice",
            title = "Introduction dene ki practice kare",
            icon = Icons.Default.AccountBox,
            color = Color(0xFF00BCD4),
            category = "Interview",
            difficulty = "Medium"
        ),
         Topic(
            id = "career_plans",
            title = "Apne career plans share kare",
            icon = Icons.Default.Build,
            color = Color(0xFF673AB7),
            category = "Interview",
            difficulty = "Medium"
        ),
        Topic(
            id = "govt_interview",
            title = "UPSC interview ki practice kare",
            icon = Icons.Default.AccountCircle,
            color = Color(0xFF795548),
            category = "Interview",
            difficulty = "Hard"
        ),
        Topic(
            id = "job_interview",
            title = "Job interview ki practice kare",
            icon = Icons.Default.Email,
            color = Color(0xFF607D8B),
            category = "Interview",
            difficulty = "Medium"
        )
    )
}

package com.yourpackage.siamai.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourpackage.siamai.ui.components.*
import com.yourpackage.siamai.ui.theme.*
import com.yourpackage.siamai.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                if (isTyping) messages.size else messages.size - 1
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                sessions = sessions,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSessionClick = { scope.launch { drawerState.close() } },
                onSettingsClick = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            TopBar(
                onMenuClick = { scope.launch { drawerState.open() } },
                onProfileClick = {}
            )
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Siam AI", color = AccentGreen, fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("How can I help you today?", color = TextSecondary, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(message = msg, onDelete = { viewModel.deleteMessage(it) })
                        }
                        if (isTyping) {
                            item { TypingIndicator() }
                        }
                    }
                }
            }
            InputBar(
                value = inputText,
                onValueChange = { viewModel.onInputChange(it) },
                onSend = { viewModel.sendMessage() }
            )
        }
    }
}

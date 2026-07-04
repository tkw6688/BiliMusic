package com.thehbc.bilimusic.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var cookieInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (authState is AuthState.Idle) {
            viewModel.generateQRCode()
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录哔哩哔哩") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("扫码登录") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("手动输入 Cookie") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        // 扫码登录页
                        when (val state = authState) {
                            is AuthState.Loading -> CircularProgressIndicator()
                            is AuthState.QRCodeReady -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Image(
                                            bitmap = state.qrBitmap.asImageBitmap(),
                                            contentDescription = "登录二维码",
                                            modifier = Modifier
                                                .size(240.dp)
                                                .padding(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            is AuthState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.generateQRCode() }) {
                                        Text("重新生成")
                                    }
                                }
                            }
                            is AuthState.Success -> {
                                Text("登录成功！\n欢迎，${state.uname}", textAlign = TextAlign.Center)
                            }
                            else -> {}
                        }
                    }
                    1 -> {
                        // 手动输入 Cookie
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedTextField(
                                value = cookieInput,
                                onValueChange = { cookieInput = it },
                                label = { Text("在此粘贴你的 Cookie 字符串") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                maxLines = 5
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loginWithCookie(cookieInput) },
                                enabled = cookieInput.isNotBlank() && authState !is AuthState.Loading
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text("一键强制登录")
                                }
                            }
                            
                            if (authState is AuthState.Error && selectedTabIndex == 1) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = (authState as AuthState.Error).message,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

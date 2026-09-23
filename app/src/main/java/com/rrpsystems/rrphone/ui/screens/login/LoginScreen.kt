package com.rrpsystems.rrphone.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.rrpsystems.rrphone.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.ui.components.rememberProfileImporter
import com.rrpsystems.rrphone.ui.theme.Rrp
import com.rrpsystems.rrphone.ui.screens.settings.TransportSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val loginState by viewModel.loginState
    var domain by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("udp") }
    val importProfile = rememberProfileImporter(
        onImported = { viewModel.applyProfile(it) },
        onError = { viewModel.showError(it) },
    )

    // Efeito para navegar se for sucesso
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess()
        }
    }

    // Fundo Gradiente Premium
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F2027),
            Color(0xFF203A43),
            Color(0xFF2C5364)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Rrp.Panel
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_rrp_clean),
                    contentDescription = "Logo RRP Systems",
                    modifier = Modifier
                        .size(100.dp)
                        .padding(bottom = 16.dp)
                )

                Text(
                    text = "RRP Softphone",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Rrp.TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Configuração da Conta SIP",
                    fontSize = 14.sp,
                    color = Rrp.TextSecondary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domínio / Servidor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text("Ramal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                TransportSelector(transport) { transport = it }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.configureSipAccount(domain, extension, password, transport) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Rrp.AccentBlue
                    ),
                    enabled = loginState !is LoginState.Loading
                ) {
                    if (loginState is LoginState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("CONFIGURAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = importProfile,
                    enabled = loginState !is LoginState.Loading,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Importar arquivo de configuração (.rrpprofile)", color = Rrp.AccentBlue)
                }

                if (loginState is LoginState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (loginState as LoginState.Error).error,
                        color = Rrp.Red,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

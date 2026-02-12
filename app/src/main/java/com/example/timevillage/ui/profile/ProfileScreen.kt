package com.example.timevillage.ui.profile

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.timevillage.ui.village.VillageViewModel
import com.example.timevillage.util.formatToTime
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun ProfileScreen(viewModel: VillageViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Состояние пользователя Firebase
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

    // Конфигурация Google Sign-In
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("597907498208-5tjpuqkklsjq0hdt189q590b7egdgptm.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Обработчик авторизации
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)

                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { taskRes ->
                        if (taskRes.isSuccessful) {
                            user = FirebaseAuth.getInstance().currentUser
                            viewModel.syncWithCloud()
                            Log.d("AUTH", "Успешный вход: ${user?.displayName}")
                        }
                    }
            } catch (e: ApiException) {
                Log.e("AUTH", "Ошибка Google Sign-In: ${e.statusCode}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ПРОФИЛЬ ИГРОКА",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.sp
            )
        )

        Spacer(Modifier.height(30.dp))

        // КАРТОЧКА ПРОФИЛЯ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2D2D2D), Color(0xFF1A1A1A))))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Аватарка из Google
                if (user?.photoUrl != null) {
                    AsyncImage(
                        model = user?.photoUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFF81C784), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = Color.Gray
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Имя пользователя
                Text(
                    text = user?.displayName ?: uiState.nickname,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (user != null) "Аккаунт подключен" else "Гостевой режим",
                    fontSize = 12.sp,
                    color = if (user != null) Color(0xFF81C784) else Color.Gray
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 20.dp),
                    thickness = 1.dp,
                    color = Color.White.copy(0.1f)
                )

                // Игровая статистика (ОСТАВИЛИ ТОЛЬКО ОБЩИЙ ТАЙМИНГ И ЗДАНИЯ)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox("ОБЩИЙ ТАЙМИНГ", uiState.globalTime.formatToTime())
                    StatBox("ЗДАНИЙ", uiState.buildings.size.toString())
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // КНОПКИ АВТОРИЗАЦИИ
        if (user == null) {
            Button(
                onClick = { authLauncher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ПРИВЯЗАТЬ GOOGLE", fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    googleSignInClient.signOut()
                    user = null
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                border = BorderStroke(1.dp, Color(0xFFEF5350).copy(0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ВЫЙТИ ИЗ АККАУНТА", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFFB74D) // Оранжевый акцент
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray,
            fontWeight = FontWeight.SemiBold
        )
    }
}
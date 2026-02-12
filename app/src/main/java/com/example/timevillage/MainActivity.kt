package com.example.timevillage

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.timevillage.data.local.VillageDatabase
import com.example.timevillage.data.repository.VillageRepository
import com.example.timevillage.ui.MainScreen
import com.example.timevillage.ui.splash.SplashScreen
import com.example.timevillage.ui.theme.SemestrTheme
import com.example.timevillage.ui.timer.TimerViewModel
import com.example.timevillage.ui.village.VillageViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : ComponentActivity() {

    private lateinit var vViewModel: VillageViewModel
    private lateinit var tViewModel: TimerViewModel

    // Лаунчер для обработки результата входа Google
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { firebaseAuthWithGoogle(it) }
        } catch (e: ApiException) {
            Log.e("AUTH_DEBUG", "Google sign in failed: ${e.statusCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = VillageDatabase.getDatabase(this)
        val repository = VillageRepository(database.villageDao())

        vViewModel = VillageViewModel(repository)
        tViewModel = TimerViewModel(repository)

        setContent {
            SemestrTheme {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf("splash") }

                LaunchedEffect(Unit) {
                    vViewModel.preloadAssets(context)
                }

                when (currentScreen) {
                    "splash" -> {
                        SplashScreen(viewModel = vViewModel) {
                            currentScreen = "game"
                        }
                    }
                    "game" -> {
                        // ПЕРЕДАЕМ колбэк для входа
                        MainScreen(
                            villageViewModel = vViewModel,
                            timerViewModel = tViewModel,
                        )
                    }
                }
            }
        }
    }

    private fun startSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("AUTH_DEBUG", "Firebase Login Success")
                    vViewModel.syncWithCloud()
                } else {
                    Log.e("AUTH_DEBUG", "Firebase Login Failed: ${task.exception}")
                }
            }
    }
}
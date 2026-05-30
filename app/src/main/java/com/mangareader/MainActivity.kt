package com.mangareader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mangareader.data.ThemeMode
import com.mangareader.data.ThemePreferences
import com.mangareader.ui.AppNavHost
import com.mangareader.ui.theme.MangaReaderTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = grants.filter { !it.value }.keys
        Timber.i("Permission result: granted=%s, denied=%s", grants.filter { it.value }.keys, denied)
        if (denied.isNotEmpty()) {
            Timber.w("Storage permissions denied: %s", denied)
            Toast.makeText(this, "Нужны разрешения для чтения файлов", Toast.LENGTH_LONG).show()
        } else {
            Timber.i("All storage permissions granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("MainActivity: onCreate, savedState=%s", if (savedInstanceState != null) "restored" else "fresh")
        enableEdgeToEdge()
        requestStoragePermissions()

        setContent {
            val themeMode by ThemePreferences.themeModeFlow(this).collectAsState(ThemeMode.SYSTEM)
            val dynamicColor by ThemePreferences.dynamicColorFlow(this).collectAsState(true)

            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MangaReaderTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }

    private fun requestStoragePermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }

        val needRequest = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) {
            permissionLauncher.launch(perms)
        }
    }
}

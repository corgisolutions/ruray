package io.github.corgisolutions.ruray

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import io.github.corgisolutions.ruray.data.db.AppDatabase
import io.github.corgisolutions.ruray.ui.screens.MainScreen
import io.github.corgisolutions.ruray.ui.theme.RURayTheme
import io.github.corgisolutions.ruray.utils.CountryUtils
import io.github.corgisolutions.ruray.utils.GovAppScanner
import io.github.corgisolutions.ruray.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    private var launchId: Long = 0L
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        Thread { CountryUtils.init(applicationContext) }.start()

        val appDb = AppDatabase.getDatabase(applicationContext)
        GovAppScanner.startPeriodicScan(applicationContext, appDb, appScope)

        launchId = savedInstanceState?.getLong("launchId")
            ?: intent.getLongExtra("LAUNCH_ID", System.currentTimeMillis())

        enableEdgeToEdge()
        setContent {
            RURayTheme(darkTheme = true) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        launchId = launchId,
                        Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("launchId", launchId)
    }
}
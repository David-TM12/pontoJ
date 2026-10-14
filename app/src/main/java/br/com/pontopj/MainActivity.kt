package br.com.pontopj

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.pontopj.location.LocationTracker
import br.com.pontopj.ui.BarChartIcon
import br.com.pontopj.ui.DashboardScreen
import br.com.pontopj.ui.HomeScreen
import br.com.pontopj.ui.Palette
import br.com.pontopj.ui.PontoTheme
import br.com.pontopj.ui.ReportsScreen
import br.com.pontopj.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PontoTheme {
                App(viewModel)
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun App(vm: AppViewModel) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = context.hasLocationPermission()
    }
    val requestPermission = {
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Android 13+: a notificação do ponto precisa desta permissão.
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(wanted.toTypedArray())
    }

    // Pede a permissão na primeira abertura.
    LaunchedEffect(Unit) {
        val needsNotification = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (!hasPermission || needsNotification) requestPermission()
    }

    // Localização em tempo real só enquanto o app está visível (economiza bateria).
    val tracker = remember { LocationTracker(context) { vm.onLocation(it) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && hasPermission) {
                tracker.start()
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = context.hasLocationPermission()
                vm.reload() // pode ter mudado pela notificação (Registrar saída)
            }
            if (event == Lifecycle.Event.ON_STOP) {
                tracker.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Palette.Gold,
        selectedTextColor = Palette.Gold,
        unselectedIconColor = Palette.Muted,
        unselectedTextColor = Palette.Muted,
        indicatorColor = Palette.GoldSoft,
    )

    Scaffold(
        containerColor = Palette.Black,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F0F0E)) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text("Ponto") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { BarChartIcon() },
                    label = { Text("Dashboard") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.DateRange, contentDescription = null) },
                    label = { Text("Relatórios") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Ajustes") },
                    colors = navColors,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = tab, label = "tabs") { current ->
                when (current) {
                    0 -> HomeScreen(
                        vm = vm,
                        hasPermission = hasPermission,
                        onRequestPermission = { requestPermission() },
                        onOpenSettings = { tab = 3 },
                    )
                    1 -> DashboardScreen(vm, onSeeAll = { tab = 2 })
                    2 -> ReportsScreen(vm)
                    else -> SettingsScreen(vm, hasPermission) { requestPermission() }
                }
            }
        }
    }
}

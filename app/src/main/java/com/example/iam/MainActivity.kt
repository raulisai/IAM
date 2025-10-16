package com.example.iam

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.example.iam.network.TokenUploader
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val healthConnectManager: HealthConnectManager by lazy { HealthConnectManager(this) }

    private var startupSyncDone = false
    private var pendingPermissionRequest: PermissionRequest? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        if (granted) getFcmToken()
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "RECORD_AUDIO granted=$granted")
        pendingPermissionRequest?.let {
            if (granted) {
                it.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                it.deny()
            }
            pendingPermissionRequest = null
        }
    }

    private val requestHealthPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        // granted contiene los permisos concedidos (strings)
        lifecycleScope.launch {
            Log.d(TAG, "Health permissions result: $granted")
            // Siempre intentar subir datos si se concedió al menos un permiso
            if (granted.isNotEmpty()) {
                Log.d(TAG, "At least one permission granted, attempting to upload health data")
                uploadHealthData()
            } else {
                Log.d(TAG, "No permissions granted.")
                showHealthPermissionRationaleDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            WebViewPage(
                url = "https://s8s23kr8-5173.usw3.devtunnels.ms",
                requestAudioPermission = {
                    requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                setPendingPermissionRequest = { request ->
                    pendingPermissionRequest = request
                }
            )
        }

        requestNotificationPermission()
        requestExactAlarmPermission()
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        // moved performStartupHealthSync to onResume to avoid permission activity closing early
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[onResume] Activity resumed, startupSyncDone=$startupSyncDone")

        // Solo ejecutar la sincronización una vez al inicio para evitar bucles
        if (!startupSyncDone) {
            startupSyncDone = true
            performStartupHealthSync()
        } else {
            // Si ya se hizo el sync inicial, solo verificar al volver a la app
            // Esto es útil si el usuario cambió permisos manualmente
            lifecycleScope.launch {
                // Retraso para dar tiempo al sistema a actualizar el estado de los permisos
                kotlinx.coroutines.delay(500)
                try {
                    val isAvailable = healthConnectManager.isAvailable()
                    Log.d(TAG, "[onResume] Health Connect available: $isAvailable")

                    if (isAvailable) {
                        val hasPermissions = healthConnectManager.hasAllPermissions()
                        Log.d(TAG, "[onResume] Has all permissions: $hasPermissions")

                        if (hasPermissions) {
                            Log.d(TAG, "[onResume] Permissions granted, uploading data")
                            uploadHealthData()
                        } else {
                            Log.d(TAG, "[onResume] Permissions still missing")
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "[onResume] Permission check failed", t)
                }
            }
        }
    }

    // Nuevo: diálogo que guía al usuario a los ajustes de Health Connect
    private fun showHealthPermissionRationaleDialog() {
        try {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Permisos de Health")
                .setMessage("La app necesita permisos en Health Connect para leer métricas. Abre Health Connect y concede los permisos en la sección de permisos de la app.")
                .setPositiveButton("Abrir Health Connect") { _, _ ->
                    try {
                        if (isHealthConnectInstalled()) {
                            openHealthConnectApp()
                        } else {
                            openHealthConnectInPlayStore()
                        }
                    } catch (_: Exception) {
                        Log.w(TAG, "Failed to open Health Connect")
                    }
                }
                .setNegativeButton("Abrir ajustes") { _, _ ->
                    // Abrir detalles de la app Health Connect o de esta app como fallback
                    try {
                        if (isHealthConnectInstalled()) {
                            openHealthConnectSettings()
                        } else {
                            openAppSettings()
                        }
                    } catch (_: Exception) {
                        Log.w(TAG, "Failed to open settings")
                    }
                }
                .setNeutralButton("Cancelar", null)
                .show()
        } catch (t: Throwable) {
            Log.w(TAG, "showHealthPermissionRationaleDialog failed", t)
        }
    }

    private fun isHealthConnectInstalled(): Boolean {
        val pm = packageManager
        val candidates = listOf("com.google.android.healthconnect.controller", "com.google.android.apps.healthdata")
        return candidates.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun openHealthConnectApp() {
        val pm = packageManager
        val pkg = when {
            isPackageInstalled("com.google.android.apps.healthdata") -> "com.google.android.apps.healthdata"
            isPackageInstalled("com.google.android.healthconnect.controller") -> "com.google.android.healthconnect.controller"
            else -> null
        }
        pkg?.let {
            val launchIntent = pm.getLaunchIntentForPackage(it)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                // abrir detalles si no hay intent de lanzamiento
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", it, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open Health Connect details", e)
                }
            }
        }
    }

    private fun openHealthConnectInPlayStore() {
        val playPkg = "com.google.android.apps.healthdata"
        try {
            val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$playPkg".toUri())
            intent.setPackage("com.android.vending")
            startActivity(intent)
        } catch (_: Exception) {
            // fallback to web
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$playPkg".toUri())
                startActivity(intent)
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to open Play Store for Health Connect", ex)
            }
        }
    }

    private fun openHealthConnectSettings() {
        // abre la pantalla de detalles de la app Health Connect
        try {
            val pkg = when {
                isPackageInstalled("com.google.android.apps.healthdata") -> "com.google.android.apps.healthdata"
                isPackageInstalled("com.google.android.healthconnect.controller") -> "com.google.android.healthconnect.controller"
                else -> null
            }
            pkg?.let {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", it, null)
                }
                startActivity(intent)
            } ?: openAppSettings()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Health Connect settings", e)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open app settings", e)
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0); true
        } catch (_: Exception) {
            false
        }
    }

    private fun performStartupHealthSync() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "[STARTUP] Checking Health Connect availability...")
                if (healthConnectManager.isAvailable()) {
                    Log.d(TAG, "[STARTUP] Health Connect is available")
                    if (healthConnectManager.hasAllPermissions()) {
                        Log.d(TAG, "[STARTUP] All permissions granted - uploading data")
                        uploadHealthData()
                    } else {
                        Log.d(TAG, "[STARTUP] Permissions missing - requesting permissions")
                        showPrePermissionDialogAndRequest()
                    }
                } else {
                    Log.d(TAG, "[STARTUP] Health Connect SDK not available on this device.")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "[STARTUP] Error while performing startup health sync", t)
            }
        }
    }

    private fun uploadHealthData() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting health data upload...")
                val dailySummary = healthConnectManager.getDailySummary()
                Log.d(TAG, "Health data retrieved: $dailySummary")
                TokenUploader.uploadHealthData(applicationContext, dailySummary)
                Log.d(TAG, "Health data upload initiated successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to upload health data", t)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> getFcmToken()
                else -> requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            getFcmToken()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Requesting permission for exact alarms.")
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { intent ->
                    startActivity(intent)
                }
            }
        }
    }

    private fun getFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d(TAG, "FCM token: $token")
                token?.let { TokenUploader.uploadToken(applicationContext, it) }
            }
    }

    // Nuevo: muestra un diálogo con la explicación y, si el usuario acepta, lanza el requestHealthPermissions
    private fun showPrePermissionDialogAndRequest() {
        try {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Acceso a métricas de salud")
                .setMessage("La app necesita acceder a tus métricas de salud (pasos, distancia, pulsaciones, sueño) para enviar un resumen diario al backend. ¿Deseas conceder estos permisos ahora?")
                .setPositiveButton("Solicitar permisos") { _, _ ->
                    try {
                        requestHealthPermissions.launch(HealthConnectManager.PERMISSIONS)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to launch Health Connect permission request", e)
                        showHealthPermissionRationaleDialog()
                    }
                }
                .setNegativeButton("Abrir ajustes") { _, _ ->
                    openHealthConnectSettings()
                }
                .setNeutralButton("Cancelar", null)
                .show()
        } catch (t: Throwable) {
            Log.w(TAG, "showPrePermissionDialogAndRequest failed", t)
        }
    }
}

@Composable
fun WebViewPage(
    url: String,
    requestAudioPermission: () -> Unit,
    setPendingPermissionRequest: (PermissionRequest) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = WebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    // Be less strict with origin check
                    if (request.origin.toString().startsWith("https://s8s23kr8-5173.usw3.devtunnels.ms")) {
                        if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            setPendingPermissionRequest(request)
                            requestAudioPermission()
                        } else {
                            request.deny()
                        }
                    } else {
                        request.deny()
                    }
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress == 100) {
                        view.post {
                            view.requestLayout()
                        }
                    }
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView },
        update = { it.loadUrl(url) }
    )
}
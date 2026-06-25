package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _currentUrl = MutableStateFlow("https://reserva-de-salas-de-estudo.vercel.app/?utm_source=android")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    fun updateUrl(url: String) {
        _currentUrl.value = url
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var networkMonitor: NetworkMonitor
    private val viewModel: MainViewModel by viewModels()
    private var activeWebView: WebView? = null

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedUrl = result.data?.getStringExtra("scanned_url")
            if (!scannedUrl.isNullOrEmpty()) {
                activeWebView?.post {
                    activeWebView?.evaluateJavascript("window.handleScannedQR('$scannedUrl')", null)
                }
            }
        }
    }

    fun openNativeQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        networkMonitor = NetworkMonitor(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)

        // Set system status & navigation bar to matching solid blue (#0066cc) or translucent
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF0066CC.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF000000.toInt())
        )

        // Preload deep links if passed on launch
        handleDeepLinkIntent(intent)

        setContent {
            MyApplicationTheme {
                val isOnline by networkMonitor.isOnline.collectAsState(initial = networkMonitor.isCurrentlyConnected())
                var showSplash by remember { mutableStateOf(true) }
                var loadProgress by remember { mutableStateOf(0) }
                var hasLoadError by remember { mutableStateOf(false) }
                var webViewInstance by remember { mutableStateOf<WebView?>(null) }
                val targetUrl by viewModel.currentUrl.collectAsState()

                // Register standard Push Notification runtime permissions for Android 13+
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    
                    // Fallback to auto-hide splash screen in case of slow loads
                    delay(3000)
                    showSplash = false
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFF5F7FA)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (hasLoadError || !isOnline) {
                            OfflineScreen(
                                onRetry = {
                                    hasLoadError = false
                                    webViewInstance?.let { web ->
                                        if (networkMonitor.isCurrentlyConnected()) {
                                            web.reload()
                                        }
                                    }
                                }
                            )
                        } else {
                            // Immersive WebView Layout
                            Box(modifier = Modifier.fillMaxSize()) {
                                ImmersiveWebView(
                                    url = targetUrl,
                                    isOnline = isOnline,
                                    onProgressChanged = { progress ->
                                        loadProgress = progress
                                        if (progress >= 95) {
                                            showSplash = false
                                        }
                                    },
                                    onErrorOccurred = {
                                        hasLoadError = true
                                    },
                                    onWebViewCreated = { webView ->
                                        webViewInstance = webView
                                        this@MainActivity.activeWebView = webView
                                    }
                                )

                                // Linear progress bar underneath the status bar
                                if (loadProgress in 1..99) {
                                    LinearProgressIndicator(
                                        progress = { loadProgress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.TopCenter),
                                        color = Color(0xFF0066cc),
                                        trackColor = Color(0x330066cc)
                                    )
                                }
                            }
                        }

                        // Seamless Splash Screen Overlay during Cold Start
                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(animationSpec = tween(500))
                        ) {
                            SplashScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data
        if (Intent.ACTION_VIEW == action && data != null) {
            val host = data.host
            val scheme = data.scheme
            if ("reservasalas" == scheme && "room" == host) {
                val roomId = data.getQueryParameter("id")
                if (!roomId.isNullOrEmpty()) {
                    val targetUrl = "https://reserva-de-salas-de-estudo.vercel.app/salas/$roomId?utm_source=android"
                    viewModel.updateUrl(targetUrl)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImmersiveWebView(
    url: String,
    isOnline: Boolean,
    onProgressChanged: (Int) -> Unit,
    onErrorOccurred: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Setup high performance companion attributes
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            
            // Set explicit hardware layers
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Set native User-Agent override
            val defaultUa = settings.userAgentString
            settings.userAgentString = "$defaultUa ReservaSalasCompanionAndroid/1.0"

            val mainActivity = context as? MainActivity
            if (mainActivity != null) {
                addJavascriptInterface(WebAppInterface(mainActivity), "AndroidBridge")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val requestUrl = request?.url?.toString() ?: return false
                    // Keep navigation inside WebView for matching domains
                    return if (requestUrl.contains("reserva-de-salas-de-estudo.vercel.app") || requestUrl.contains("vercel.app")) {
                        false
                    } else {
                        // Open external links in external browser
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onProgressChanged(10)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onProgressChanged(100)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onErrorOccurred()
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onProgressChanged(newProgress)
                }
            }

            // Standardize back-button flow inside web history
            onWebViewCreated(this)
        }
    }

    // Reactive caching strategies
    LaunchedEffect(isOnline) {
        webView.settings.cacheMode = if (isOnline) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
    }

    // Load URL when target matches
    LaunchedEffect(url) {
        webView.loadUrl(url)
    }

    // Handle back dispatcher
    androidx.activity.compose.BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    // Interop rendering
    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize().testTag("companion_webview")
    )
}

@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0066cc)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Elegant simple book/door outline vector as visual brand loader
            Icon(
                imageVector = Icons.Filled.Info, // High-visibility info standard symbol fallback
                contentDescription = "Logo Reserva de Salas",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Reserva de Salas",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Estudo e Aprendizado Integrado",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun OfflineScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0066cc))
            .testTag("offline_screen")
    ) {
        // Immersive UI Toolbar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Reserva de Salas",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
            }
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }

        // Material rounded body layout matching: rounded-t-[32px] bg-[#FDFBFF]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFFFDFBFF))
                .padding(24.dp)
        ) {
            Text(
                text = "Salas Disponíveis",
                color = Color(0xFF0F172A),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Biblioteca Central • Modo Offline",
                color = Color(0xFF64748B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Warning panel matching the design's `bg-orange-50 border border-orange-100` layout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(1.dp, Color(0xFFFFEDD5), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Alerta",
                        tint = Color(0xFFEA580C),
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Column {
                        Text(
                            text = "Sem conexão com a Internet",
                            color = Color(0xFF7C2D12),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Modo Offline: Não foi possível carregar a reserva de salas. Algumas funcionalidades podem estar limitadas até a conexão ser restabelecida.",
                            color = Color(0xFF7C2D12),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Design Mock items showing state representation
            OfflineRoomRow(
                number = "01",
                name = "Sala de Estudo A",
                status = "Disponível localmente",
                isAvailable = true,
                onAction = onRetry
            )

            Spacer(modifier = Modifier.height(12.dp))

            OfflineRoomRow(
                number = "02",
                name = "Sala de Estudo B",
                status = "Ocupada (Sem conexão)",
                isAvailable = false,
                onAction = onRetry
            )

            Spacer(modifier = Modifier.weight(1f))

            // Styled Primary matching #0066cc container background
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("retry_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0066cc)
                ),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = "Tentar Novamente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun OfflineRoomRow(
    number: String,
    name: String,
    status: String,
    isAvailable: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isAvailable) Color(0xFFEFF6FF) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = if (isAvailable) Color(0xFF2563EB) else Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Column {
                Text(
                    text = name,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = status,
                    color = if (isAvailable) Color(0xFF16A34A) else Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (isAvailable) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0066cc)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = "Reconectar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFFE2E8F0),
                    disabledContentColor = Color(0xFF94A3B8)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = "Ocupada",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Keep a legacy function for Greeting with defaults to satisfy any existing screenshot test or templates
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Olá, $name!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0066cc)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bem-vindo ao aplicativo Reserva de Salas de Estudo.",
                fontSize = 14.sp,
                color = Color(0xFF49454F)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}

class WebAppInterface(private val activity: MainActivity) {
    @JavascriptInterface
    fun startQRScanner() {
        activity.openNativeQRScanner()
    }
}

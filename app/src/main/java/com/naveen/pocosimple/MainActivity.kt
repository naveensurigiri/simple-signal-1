package com.naveen.pocosimple

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.provider.Settings.Global
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SimpleSignalApp() }
    }
}

// ---- Colours ----
private val Black = Color(0xFF000000)
private val CardBg = Color(0xFF15171E)
private val GraphBg = Color(0xFF0B0D12)
private val White = Color(0xFFFFFFFF)
private val Muted = Color(0xFF9AA0A6)
private val Teal = Color(0xFF2DD4BF)
private val GreenGood = Color(0xFF34D399)
private val AmberOkay = Color(0xFFFBBF24)
private val RedWeak = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    background = Black,
    surface = CardBg,
    primary = Teal,
    onPrimary = Black,
    onBackground = White,
    onSurface = White
)

private enum class AppTab(val label: String) {
    HOME("Home"), SIMS("SIMs"), SPEED("Speed"), WIFI("Wi-Fi"), ABOUT("About")
}

@Composable
fun SimpleSignalApp() {
    MaterialTheme(colorScheme = DarkColors) {
        Box(modifier = Modifier.fillMaxSize().background(Black)) {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var permissionTick by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<NetReading?>(null) }
    val device = remember { DeviceInfo.read() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick++ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    LaunchedEffect(permissionTick) {
        while (true) {
            reading = NetworkReader.read(context)
            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (AppTab.entries[tabIndex]) {
                AppTab.HOME -> HomeTab(reading, device, context)
                AppTab.SIMS -> SimsTab(reading, context)
                AppTab.SPEED -> SpeedTab()
                AppTab.WIFI -> WifiTab(reading, context)
                AppTab.ABOUT -> AboutTab(device, reading)
            }
        }
        BottomBar(selected = tabIndex, onSelect = { tabIndex = it })
    }
}

// ---- Home ----
@Composable
private fun HomeTab(reading: NetReading?, device: DeviceDetails, context: Context) {
    Header()
    if (!device.supported) UnsupportedBanner()

    val onWifi = reading?.onWifi == true
    val signal = if (onWifi) reading?.wifiRssi else reading?.dataDbm
    val quality = if (onWifi) wifiQuality(signal) else signalQuality(signal)

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (onWifi) "WI-FI SIGNAL" else "MOBILE SIGNAL",
            color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        SignalRing(dbm = signal, quality = quality)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                reading == null -> "Reading signal…"
                onWifi -> reading.wifiName ?: "Connected to Wi-Fi"
                reading.onMobile -> listOfNotNull(reading.dataCarrier, reading.dataTech, reading.dataBand).joinToString(" • ")
                else -> "No active connection"
            },
            color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center
        )
    }

    QuickControlsCard(context)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton(text = "Switch SIM", modifier = Modifier.weight(1f), filled = true) {
            openSimSettings(context)
        }
        BigButton(text = "Wi-Fi", modifier = Modifier.weight(1f), filled = false) {
            openSettings(context, Settings.Panel.ACTION_WIFI)
        }
    }
}

@Composable
private fun SignalRing(dbm: Int?, quality: Quality) {
    val progress = when {
        dbm == null -> 0.05f
        dbm >= -85 -> 1f
        dbm >= -95 -> 0.82f
        dbm >= -105 -> 0.62f
        dbm >= -115 -> 0.40f
        else -> 0.2f
    }
    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = CardBg,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = quality.color,
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = dbm?.toString() ?: "--", color = White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            Text(text = "dBm", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(5.dp))
            Text(text = quality.word, color = quality.color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickControlsCard(context: Context) {
    val wifi = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            refresh++
            delay(1500)
        }
    }
    val wifiOn = remember(refresh) { runCatching { wifi.isWifiEnabled }.getOrDefault(false) }
    val mobileOn = remember(refresh) {
        runCatching {
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }.getOrDefault(false)
    }
    val locationOn = remember(refresh) { runCatching { lm.isLocationEnabled }.getOrDefault(false) }
    val airplaneOn = remember(refresh) {
        runCatching { Global.getInt(context.contentResolver, Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
    }

    InfoCard {
        Text("Quick controls", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        QuickControlRow("Wi-Fi", wifiOn) { openSettings(context, Settings.Panel.ACTION_WIFI) }
        QuickControlRow("Mobile data", mobileOn) { openSettings(context, Settings.Panel.ACTION_MOBILE_DATA) }
        QuickControlRow("Location", locationOn) { openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
        QuickControlRow("Airplane mode", airplaneOn) { openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
        Text("Tap a row to open Android settings. System restrictions prevent ordinary apps from changing these switches directly.",
            color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun QuickControlRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Pill(if (enabled) "On" else "Off", if (enabled) GreenGood else Muted)
        Spacer(Modifier.width(8.dp))
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

private fun openSimSettings(context: Context) {
    val candidates = listOf(
        Intent("miui.intent.action.MANAGE_SIM_CARDS"),
        Intent("android.settings.DATA_ROAMING_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) { }
    }
    openSettings(context, Settings.ACTION_SETTINGS)
}

// ---- SIMs ----
@Composable
private fun SimsTab(reading: NetReading?, context: Context) {
    Text(text = "Your SIMs", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
    Text(text = "Each SIM's signal, band, and whether it's used for mobile data.", fontSize = 15.sp, color = Muted)

    val sims = reading?.sims.orEmpty()
    if (sims.isEmpty()) {
        InfoCard {
            Text(
                text = "No SIM found yet. Allow phone access when asked, and check that your SIMs are inserted.",
                fontSize = 15.sp,
                color = Muted
            )
        }
    }

    sims.forEach { sim ->
        val quality = signalQuality(sim.dbm)
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "SIM ${sim.slot}", fontSize = 15.sp, color = Muted)
                if (sim.isData) Pill(text = "Used for data", color = Teal)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = sim.carrier, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (sim.dbm == null) "No signal reading" else "${sim.dbm} dBm  •  ${quality.word}",
                fontSize = 16.sp,
                color = quality.color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Band: ${sim.band ?: "Not reported"}  •  ${sim.tech ?: "Mobile"}",
                fontSize = 15.sp,
                color = Muted
            )
        }
    }

    BigButton(text = "Switch data SIM", modifier = Modifier.fillMaxWidth(), filled = true) {
        openSettings(context, Settings.ACTION_WIRELESS_SETTINGS)
    }
    Text(
        text = "Android opens Network & internet. Tap SIMs, then pick the SIM you want for mobile data.",
        fontSize = 13.sp,
        color = Muted
    )
}

// ---- Speed (centre tab) ----
@Composable
private fun SpeedTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(0.0) }
    var result by remember { mutableStateOf<Double?>(null) }
    var failed by remember { mutableStateOf(false) }
    var downHist by remember { mutableStateOf(listOf<Float>()) }
    var upHist by remember { mutableStateOf(listOf<Float>()) }
    var downNow by remember { mutableStateOf(0.0) }
    var upNow by remember { mutableStateOf(0.0) }
    var usage by remember { mutableStateOf<UsageToday?>(null) }

    LaunchedEffect(Unit) {
        TrafficMonitor.reset()
        while (true) {
            delay(1000)
            val (down, up) = TrafficMonitor.sample()
            downNow = down; upNow = up
            downHist = (downHist + down.toFloat()).takeLast(40)
            upHist = (upHist + up.toFloat()).takeLast(40)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            usage = withContext(Dispatchers.IO) { runCatching { UsageReader.today(context) }.getOrNull() }
            delay(15000)
        }
    }

    Text("Speed & data", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
    Text("Run a speed test and watch live network activity.", fontSize = 15.sp, color = Muted)

    InfoCard {
        Text("Internet speed test", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 12.dp.toPx()
                    drawArc(CardBg, 135f, 270f, false,
                        androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                        androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round))
                    val fraction = if (running) (live / 200.0).toFloat().coerceIn(0.04f, 1f)
                        else ((result ?: 0.0) / 200.0).toFloat().coerceIn(0f, 1f)
                    drawArc(Teal, 135f, 270f * fraction, false,
                        androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                        androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (running) "%.1f".format(live) else result?.let { "%.1f".format(it) } ?: "—",
                        color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text("Mbps", color = Muted, fontSize = 14.sp)
                    Text(if (running) "TESTING" else if (result != null) "DOWNLOAD" else "READY",
                        color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (failed) Text("Test failed. Check your connection and try again.", color = RedWeak, fontSize = 14.sp)
        BigButton(text = if (running) "Testing…" else "Start test",
            modifier = Modifier.fillMaxWidth(), filled = true) {
            if (!running) {
                running = true; failed = false; live = 0.0
                scope.launch {
                    val measured = SpeedTester.run { live = it }
                    running = false
                    if (measured == null) failed = true else result = measured
                }
            }
        }
        Text("Download test only. Upload testing is not implemented in the supplied NetTools.kt.",
            color = Muted, fontSize = 12.sp)
    }

    InfoCard {
        Text("Live network activity", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Download", color = Muted, fontSize = 13.sp)
                Text(formatRate(downNow), color = Teal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Upload", color = Muted, fontSize = 13.sp)
                Text(formatRate(upNow), color = GreenGood, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        LiveGraph(downHist, upHist)
        Text("Teal: download  •  Green: upload  •  Updates every second", color = Muted, fontSize = 12.sp)
    }

    InfoCard {
        Text("Data used today", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
        Spacer(Modifier.height(8.dp))
        val u = usage
        if (u == null || (u.mobileBytes == null && u.wifiBytes == null)) {
            Text("Usage access may be required to show today's totals.", color = Muted, fontSize = 14.sp)
            BigButton("Allow usage access", Modifier.fillMaxWidth(), filled = false) {
                openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Mobile", color = Muted, fontSize = 13.sp)
                    Text(formatBytes(u.mobileBytes), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Wi-Fi", color = Muted, fontSize = 13.sp)
                    Text(formatBytes(u.wifiBytes), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---- Wi-Fi ----
@Composable
private fun WifiTab(reading: NetReading?, context: Context) {
    val onWifi = reading?.onWifi == true
    val mobileSim = reading?.sims?.firstOrNull { it.isData }

    Text(text = "Wi-Fi", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = White)
    Text(text = "Your Wi-Fi connection at a glance.", fontSize = 15.sp, color = Muted)

    InfoCard {
        if (onWifi) {
            Text(text = "Connected", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GreenGood)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = reading?.wifiName ?: "Wi-Fi network",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Spacer(modifier = Modifier.height(10.dp))
            val quality = wifiQuality(reading?.wifiRssi)
            Text(text = "Signal: ${quality.word}", fontSize = 18.sp, color = quality.color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Band: ${reading?.wifiBand ?: "Not reported"}", fontSize = 15.sp, color = Muted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Link speed: ${reading?.wifiMbps?.toString() ?: "--"} Mbps", fontSize = 15.sp, color = Muted)
        } else {
            Text(text = "Not connected", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Turn on Wi-Fi and join a network to see it here.", fontSize = 15.sp, color = Muted)
        }
    }

    InfoCard {
        Text(text = "Compared with mobile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (mobileSim == null) {
                "Mobile data: not connected."
            } else {
                "Mobile data: ${mobileSim.carrier}, band ${mobileSim.band ?: "not reported"}, ${mobileSim.tech ?: "Mobile"}."
            },
            fontSize = 15.sp,
            color = Muted
        )
    }

    BigButton(text = "Open Wi-Fi settings", modifier = Modifier.fillMaxWidth(), filled = true) {
        openSettings(context, Settings.ACTION_WIFI_SETTINGS)
    }
    Text(
        text = "The network name can show as hidden until you allow location access.",
        fontSize = 13.sp,
        color = Muted
    )
}

// ---- About ----
@Composable
private fun AboutTab(device: DeviceDetails, reading: NetReading?) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SignalLogo(logoSize = 96.dp, color = Teal)
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Simple Signal", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = White)
        Text(text = "Version 1.2", fontSize = 15.sp, color = Muted)
        Text(text = "Created by Veen's Technologies", fontSize = 15.sp, color = Muted)
    }

    if (!device.supported) UnsupportedBanner()

    InfoCard {
        Text(text = "Your phone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
        Spacer(modifier = Modifier.height(6.dp))
        KeyValue("Phone", device.marketName)
        KeyValue("Model number", device.modelNumber)
        KeyValue("Codename", device.codename)
        KeyValue("Chip (SoC)", device.soc)
        KeyValue("Modem (baseband)", device.baseband)
        KeyValue("Android", device.androidVersion)
        KeyValue("Security patch", device.securityPatch)
        KeyValue("Build", device.buildName)
        KeyValue("Data network", reading?.dataTech ?: "Not reported")
        KeyValue("Data band", reading?.dataBand ?: "Not reported")
    }

    InfoCard {
        Text(text = "About this app", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Simple Signal shows your signal, SIMs, Wi-Fi, and data in plain words. It is built for the POCO F4 and shows a warning on other phones.",
            fontSize = 15.sp,
            color = Muted
        )
    }
}

// ---- Shared pieces ----
@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SignalLogo(logoSize = 34.dp, color = Teal)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "Simple Signal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White)
    }
}

@Composable
private fun UnsupportedBanner() {
    InfoCard {
        Text(
            text = "Built for POCO F4 only. Some readings may not work on this phone.",
            fontSize = 15.sp,
            color = AmberOkay
        )
    }
}

@Composable
private fun LiveGraph(down: List<Float>, up: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GraphBg)
    ) {
        val peak = maxOf(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f, 100f)
        fun drawLineFor(points: List<Float>, color: Color) {
            if (points.size < 2) return
            val path = Path()
            points.forEachIndexed { i, v ->
                val x = size.width * i / (points.size - 1)
                val y = size.height - (v / peak) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = fillPath, color = color.copy(alpha = 0.10f), style = Fill)
            drawPath(path = path, color = color.copy(alpha = 0.22f), style = Stroke(width = 9f))
            drawPath(path = path, color = color, style = Stroke(width = 3.5f))
        }
        drawLineFor(down, Teal)
        drawLineFor(up, GreenGood)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = Muted)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = White,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .then(Modifier)
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean,
    onClick: () -> Unit
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Black)
        ) {
            Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Muted)
        ) {
            Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = White)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(CardBg)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEachIndexed { index, tab ->
            val active = index == selected
            if (tab == AppTab.SPEED) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (active) Teal else Teal.copy(alpha = 0.35f))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Speed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Black)
                }
            } else {
                Text(
                    text = tab.label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Teal else Muted,
                    modifier = Modifier
                        .clickable { onSelect(index) }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ---- Plain-word ratings and formatting ----
private data class Quality(val word: String, val color: Color)

private fun signalQuality(dbm: Int?): Quality = when {
    dbm == null -> Quality("No reading", Muted)
    dbm >= -90 -> Quality("Great", GreenGood)
    dbm >= -100 -> Quality("Good", GreenGood)
    dbm >= -110 -> Quality("Okay", AmberOkay)
    else -> Quality("Weak", RedWeak)
}

private fun wifiQuality(rssi: Int?): Quality = when {
    rssi == null -> Quality("No reading", Muted)
    rssi >= -60 -> Quality("Great", GreenGood)
    rssi >= -70 -> Quality("Good", GreenGood)
    rssi >= -80 -> Quality("Okay", AmberOkay)
    else -> Quality("Weak", RedWeak)
}

private fun speedWord(mbps: Double): String = when {
    mbps < 5 -> "Slow. Pages may take a while."
    mbps < 25 -> "OK for videos."
    else -> "Fast."
}

private fun formatRate(kbps: Double): String =
    if (kbps >= 1000.0) "%.2f Mbps".format(kbps / 1000.0) else "%.0f Kbps".format(kbps)

private fun formatBytes(bytes: Long?): String = when {
    bytes == null -> "--"
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    else -> "%.0f MB".format(bytes / 1_000_000.0)
}

// ---- Open phone settings, with a fallback if a page is missing ----
private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

package com.naveen.pocosimple

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

/** Device-wide speed, sampled once per second for the live graph. */
object TrafficMonitor {
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    fun reset() {
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()
    }

    /** Returns download and upload in kilobits per second. */
    fun sample(): Pair<Double, Double> {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()
        val secs = (now - lastTime).coerceAtLeast(1L) / 1000.0
        val down = (rx - lastRx).coerceAtLeast(0L) * 8 / 1000.0 / secs
        val up = (tx - lastTx).coerceAtLeast(0L) * 8 / 1000.0 / secs
        lastRx = rx
        lastTx = tx
        lastTime = now
        return down to up
    }
}

data class UsageToday(val mobileBytes: Long?, val wifiBytes: Long?)

/** Data used since midnight. Needs the Usage access permission, granted once in Settings. */
object UsageReader {
    fun today(context: Context): UsageToday {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        return UsageToday(
            mobileBytes = bytesFor(nsm, ConnectivityManager.TYPE_MOBILE, start, end),
            wifiBytes = bytesFor(nsm, ConnectivityManager.TYPE_WIFI, start, end)
        )
    }

    private fun bytesFor(nsm: NetworkStatsManager, type: Int, start: Long, end: Long): Long? = try {
        val bucket = nsm.querySummaryForDevice(type, null, start, end)
        bucket.rxBytes + bucket.txBytes
    } catch (e: Exception) {
        null
    }
}

/** Downloads from four connections for about 6 seconds and reports Mbps. */
object SpeedTester {
    suspend fun run(onProgress: (Double) -> Unit): Double? = coroutineScope {
        val total = AtomicLong(0L)
        val start = System.nanoTime()
        val deadline = start + 6_000_000_000L

        val jobs = List(4) {
            async(Dispatchers.IO) {
                try {
                    val conn = URL("https://speed.cloudflare.com/__down?bytes=50000000")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val buffer = ByteArray(64 * 1024)
                    conn.inputStream.use { input ->
                        while (System.nanoTime() < deadline) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            total.addAndGet(n.toLong())
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    // A dropped connection just ends that stream.
                }
            }
        }

        while (System.nanoTime() < deadline) {
            delay(300)
            val secs = (System.nanoTime() - start) / 1_000_000_000.0
            if (secs > 0.0) onProgress(total.get() * 8 / 1_000_000.0 / secs)
        }
        jobs.forEach { it.cancel() }

        val secs = (System.nanoTime() - start) / 1_000_000_000.0
        val bytes = total.get()
        if (bytes < 200_000L) null else bytes * 8 / 1_000_000.0 / secs
    }
}

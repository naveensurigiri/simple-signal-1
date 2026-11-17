package com.naveen.pocosimple

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SimReading(
    val slot: Int,
    val carrier: String,
    val isData: Boolean,
    val dbm: Int?,
    val band: String?,
    val tech: String?
)

data class NetReading(
    val onWifi: Boolean,
    val onMobile: Boolean,
    val sims: List<SimReading>,
    val dataCarrier: String?,
    val dataTech: String?,
    val dataDbm: Int?,
    val dataBand: String?,
    val wifiName: String?,
    val wifiRssi: Int?,
    val wifiMbps: Int?,
    val wifiBand: String?
)

object NetworkReader {

    fun hasPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun read(context: Context): NetReading = withContext(Dispatchers.IO) { readNow(context) }

    @SuppressLint("MissingPermission")
    private fun readNow(context: Context): NetReading {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val onMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val sims = mutableListOf<SimReading>()
        var dataCarrier: String? = null
        var dataTech: String? = null
        var dataDbm: Int? = null
        var dataBand: String? = null

        if (hasPhonePermission(context)) {
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val defaultDataSub = SubscriptionManager.getDefaultDataSubscriptionId()

            for (info in subManager.activeSubscriptionInfoList.orEmpty()) {
                val tm = baseTm.createForSubscriptionId(info.subscriptionId)
                val dbm = readDbm(tm)
                val band = readBand(tm)
                val tech = techName(tm.dataNetworkType)
                val isData = info.subscriptionId == defaultDataSub
                val name = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${info.simSlotIndex + 1}"
                sims += SimReading(info.simSlotIndex + 1, name, isData, dbm, band, tech)
                if (isData) {
                    dataCarrier = name
                    dataDbm = dbm
                    dataTech = tech
                    dataBand = band
                }
            }
        }

        var wifiName: String? = null
        var wifiRssi: Int? = null
        var wifiMbps: Int? = null
        var wifiBand: String? = null
        if (onWifi) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            wifiName = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            wifiRssi = info?.rssi?.takeIf { it > -127 }
            wifiMbps = info?.linkSpeed?.takeIf { it > 0 }
            val freq = info?.frequency ?: 0
            wifiBand = when {
                freq >= 5925 -> "6 GHz"
                freq >= 4900 -> "5 GHz"
                freq >= 2400 -> "2.4 GHz"
                else -> null
            }
        }

        return NetReading(
            onWifi = onWifi,
            onMobile = onMobile,
            sims = sims,
            dataCarrier = dataCarrier,
            dataTech = dataTech,
            dataDbm = dataDbm,
            dataBand = dataBand,
            wifiName = wifiName,
            wifiRssi = wifiRssi,
            wifiMbps = wifiMbps,
            wifiBand = wifiBand
        )
    }

    @SuppressLint("MissingPermission")
    private fun readDbm(tm: TelephonyManager): Int? = try {
        tm.signalStrength?.cellSignalStrengths?.maxByOrNull { it.dbm }?.dbm
    } catch (e: SecurityException) {
        null
    }

    /** Band of the registered cell, for example B3 or n78. Needs Android 11 or newer. */
    @SuppressLint("MissingPermission")
    private fun readBand(tm: TelephonyManager): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val cell = tm.allCellInfo?.firstOrNull { it.isRegistered } ?: return null
            when (cell) {
                is CellInfoLte -> cell.cellIdentity.bands
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { "B$it" }
                is CellInfoNr -> (cell.cellIdentity as? CellIdentityNr)?.bands
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { "n$it" }
                else -> null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun techName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        else -> "Mobile"
    }
}

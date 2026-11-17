package com.naveen.pocosimple

import android.os.Build

data class DeviceDetails(
    val marketName: String,
    val modelNumber: String,
    val codename: String,
    val soc: String,
    val baseband: String,
    val securityPatch: String,
    val androidVersion: String,
    val buildName: String,
    val supported: Boolean
)

object DeviceInfo {
    fun read(): DeviceDetails {
        val supported = Build.MODEL.contains("22021211RI") || Build.DEVICE.equals("munch", ignoreCase = true)
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim().ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }
        val baseband = try {
            @Suppress("DEPRECATION")
            Build.getRadioVersion() ?: "Not reported"
        } catch (e: Exception) {
            "Not reported"
        }
        return DeviceDetails(
            marketName = if (supported) "POCO F4" else "${Build.MANUFACTURER} ${Build.MODEL}",
            modelNumber = Build.MODEL,
            codename = Build.DEVICE,
            soc = soc,
            baseband = baseband,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            buildName = Build.DISPLAY,
            supported = supported
        )
    }
}

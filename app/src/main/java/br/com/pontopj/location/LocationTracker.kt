package br.com.pontopj.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import br.com.pontopj.data.LocationFix
import br.com.pontopj.data.PtBR

/** Acompanha a localização em tempo real enquanto o app está visível. */
class LocationTracker(
    context: Context,
    private val onFix: (LocationFix) -> Unit,
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val listener = LocationListener { loc ->
        onFix(LocationFix(loc.latitude, loc.longitude, loc.accuracy, loc.time))
    }

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        val enabled = { provider: String ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        val providers = if (enabled(LocationManager.FUSED_PROVIDER)) {
            listOf(LocationManager.FUSED_PROVIDER)
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter(enabled)
        }
        providers.forEach { provider ->
            try {
                manager.getLastKnownLocation(provider)?.let { listener.onLocationChanged(it) }
                manager.requestLocationUpdates(provider, 3_000L, 5f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                // permissão revogada enquanto o app estava aberto
            } catch (e: IllegalArgumentException) {
                // provedor indisponível neste aparelho
            }
        }
    }

    fun stop() {
        runCatching { manager.removeUpdates(listener) }
    }
}

/** Converte coordenadas em endereço legível. O callback sempre roda na thread principal. */
fun reverseGeocode(context: Context, lat: Double, lng: Double, callback: (String?) -> Unit) {
    if (!Geocoder.isPresent()) {
        callback(null)
        return
    }
    val main = Handler(Looper.getMainLooper())
    val geocoder = Geocoder(context, PtBR)

    if (Build.VERSION.SDK_INT >= 33) {
        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                val text = addresses.firstOrNull()?.toLine()
                main.post { callback(text) }
            }

            override fun onError(errorMessage: String?) {
                main.post { callback(null) }
            }
        })
    } else {
        Thread {
            @Suppress("DEPRECATION")
            val result = runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull()
            val text = result?.firstOrNull()?.toLine()
            main.post { callback(text) }
        }.start()
    }
}

private fun Address.toLine(): String? =
    if (maxAddressLineIndex >= 0) {
        getAddressLine(0)
    } else {
        listOfNotNull(thoroughfare, subLocality, locality).joinToString(", ").ifEmpty { null }
    }

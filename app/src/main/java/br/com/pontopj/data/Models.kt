package br.com.pontopj.data

/** Um período de trabalho (entrada e, depois, saída). O valor da hora fica gravado no registro. */
data class WorkSession(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long? = null,
    val rate: Double,
    val inLat: Double? = null,
    val inLng: Double? = null,
    val inAddress: String? = null,
    val outLat: Double? = null,
    val outLng: Double? = null,
    val outAddress: String? = null,
    val manual: Boolean = false,
) {
    val isActive: Boolean get() = endMillis == null

    fun durationMillis(now: Long): Long = ((endMillis ?: now) - startMillis).coerceAtLeast(0L)

    fun earnings(now: Long): Double = durationMillis(now) / 3_600_000.0 * rate
}

data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val timeMillis: Long,
)

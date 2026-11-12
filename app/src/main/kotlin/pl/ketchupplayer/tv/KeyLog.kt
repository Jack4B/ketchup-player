package pl.ketchupplayer.tv

/**
 * Linia diagnostyczna klawisza nawigacji (tylko build debug, tag `KeyGuard`, poziom INFO: Timber.d był odfiltrowany
 * przez RedactingTree, stąd pusty `keyguard.txt` w raporcie z boxa). Format stały, bo parsuje go
 * `tools/tvbox/analyze_keys.sh`: `KG t=… key=… rep=… age=… gap=… dev=… src=… drop=0|1`.
 *
 *  - age: ile ms zdarzenie czekało w kolejce (now − eventTime); duże = zacięcie głównego wątku,
 *  - gap: odstęp od poprzedniego zdarzenia TEGO klawisza (także odrzuconego); −1 = pierwsze; krótki odstęp
 *    przy rep=0 oznacza duplikat (pilot wysłał kilka naciśnięć na jedno wciśnięcie).
 */
internal object KeyLog {
    const val TAG = "KeyGuard"

    /** Jedno zdarzenie klawisza do zalogowania. */
    data class Record(
        val nowMs: Long,
        val keyCode: Int,
        val repeat: Int,
        val ageMs: Long,
        val gapMs: Long?,
        val deviceId: Int,
        val source: Int,
        val dropped: Boolean,
    )

    fun line(r: Record): String =
        "KG t=${r.nowMs} key=${r.keyCode} rep=${r.repeat} age=${r.ageMs} gap=${r.gapMs ?: -1} dev=${r.deviceId} " +
            "src=${r.source.toString(HEX)} drop=${if (r.dropped) 1 else 0}"

    private const val HEX = 16
}

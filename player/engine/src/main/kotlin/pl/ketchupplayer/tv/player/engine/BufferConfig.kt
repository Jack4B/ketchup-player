package pl.ketchupplayer.tv.player.engine

/** Czasy bufora dla `DefaultLoadControl` w ms (policzone z profilu w warstwie aplikacji; silnik nie zna ustawień). */
data class BufferConfig(
    val minMs: Int,
    val maxMs: Int,
    val playbackMs: Int,
    val rebufferMs: Int,
)

/** Źródło aktualnej konfiguracji bufora przy tworzeniu odtwarzacza; null = domyślne wartości Media3. */
fun interface BufferConfigSource {
    fun current(): BufferConfig?
}

package pl.ketchupplayer.tv.core.common

/** Zegar wstrzykiwany zamiast `System.currentTimeMillis()` (testy na wirtualnym czasie). */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}

package pl.ketchupplayer.tv.core.common

import android.util.Log
import timber.log.Timber

/** Timber tree, który nigdy nie zapisuje sekretów (§8.4). */
class RedactingTree(private val minPriority: Int = Log.INFO) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= minPriority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val safe = Redactor.redact(message)
        val full = if (t == null) safe else safe + "\n" + Redactor.redact(Log.getStackTraceString(t))
        Log.println(priority, tag ?: "Ketchup", full)
    }
}

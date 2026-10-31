/**
 * Reguły zależności modułów (docs/KONSPEKT.md §3.2). Czysta logika — testowalna bez Gradle.
 *
 * - feature:* nie zależy od innego feature:*
 * - core:model, core:policy — nie zależą od modułów android (tylko od siebie nawzajem / core:model)
 * - provider:* nie zależy od core:database ani od core:ui / core:designsystem / feature:*
 * - core:* nie zależy od feature:*, provider:*, player:*, epg:*, integration:*
 */
object ModuleRules {
    private val pureJvm = setOf(":core:model", ":core:policy")

    data class Violation(val from: String, val to: String, val reason: String) {
        override fun toString() = "$from -> $to: $reason"
    }

    fun check(graph: Map<String, Set<String>>): List<Violation> {
        val out = mutableListOf<Violation>()
        for ((from, deps) in graph) {
            for (to in deps) {
                violation(from, to)?.let { out += Violation(from, to, it) }
            }
        }
        return out
    }

    private fun violation(from: String, to: String): String? = when {
        from.startsWith(":feature:") && to.startsWith(":feature:") && from != to ->
            "feature nie może zależeć od innego feature (nawigacja przez :app)"
        from in pureJvm && to !in pureJvm -> "moduł czystej JVM może zależeć tylko od modułów czystej JVM"
        from.startsWith(":provider:") && (to == ":core:database" || to == ":core:ui" ||
            to == ":core:designsystem" || to.startsWith(":feature:")) ->
            "provider nie zna Room ani UI"
        from.startsWith(":core:") && listOf(":feature:", ":provider:", ":player:", ":epg:", ":integration:")
            .any { to.startsWith(it) } -> "core nie zależy od warstw wyższych"
        else -> null
    }
}

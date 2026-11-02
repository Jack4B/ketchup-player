package pl.ketchupplayer.tv

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult

/**
 * Pomiar układu ekranu z drzewa semantyki (bez zrzutów): element poza ekranem w poziomie, czcionka poniżej minimum,
 * zbyt mały cel fokusu/dotyku oraz teksty nachodzące na siebie lub „sklejone”. Test ma zostawić listę problemów pustą.
 */
object LayoutAudit {
    /** Minimum czytelności z 3 m (Android TV: 14 sp absolutne minimum). */
    const val MIN_FONT_SP = 14f

    /** Cel fokusu/dotyku: mniejszy niż 36 dp trudno trafić i źle wygląda ramka fokusu. */
    const val MIN_TARGET_DP = 36f

    private const val OVERLAP_PX = 4f
    private const val TOUCH_GAP_DP = 4f
    private const val CLIP_MARGIN_DP = 24f

    data class Issue(
        val screen: String,
        val rule: String,
        val detail: String,
    ) {
        override fun toString() = "[$screen] $rule: $detail"
    }

    private class Screen(
        val name: String,
        val width: Float,
        val height: Float,
        val density: Float,
    ) {
        val issues = mutableListOf<Issue>()
        val texts = mutableListOf<Pair<String, Rect>>()

        fun add(
            rule: String,
            detail: String,
        ) {
            issues += Issue(name, rule, detail)
        }
    }

    fun run(
        screen: String,
        compose: AndroidComposeTestRule<*, *>,
    ): List<Issue> {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val s = Screen(screen, root.boundsInRoot.right, root.boundsInRoot.bottom, root.layoutInfo.density.density)
        walk(root, s)
        checkNeighbours(s)
        return s.issues.distinct()
    }

    private fun walk(
        n: SemanticsNode,
        s: Screen,
    ) {
        // Treść pod panelem Ustawień jest wyłączona z semantyki (clearAndSetSemantics): fokus jej nie widzi.
        if (n.config.isClearingSemantics && n.parent != null && n.children.isNotEmpty()) return
        val b = n.boundsInRoot
        if (b.width > 1f && b.height > 1f) checkNode(n, b, s)
        n.children.forEach { walk(it, s) }
    }

    private fun checkNode(
        n: SemanticsNode,
        b: Rect,
        s: Screen,
    ) {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        // Pola tekstowe mają wysokość linii, ale celem fokusu jest cała ramka: nie mierzymy ich jako celów.
        val clickable = n.config.contains(SemanticsActions.OnClick) && !n.config.contains(SemanticsActions.SetText)
        if (text != null || clickable) {
            val label =
                text ?: n.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    ?.joinToString()
                    .orEmpty()
            checkBounds(label, clickable, b, s)
        }
        if (text != null && n.children.none { it.config.contains(SemanticsProperties.Text) }) {
            s.texts += text to usedBounds(n, b)
            fontSizeSp(n)?.takeIf { it < MIN_FONT_SP }?.let { s.add("mała czcionka", "'${text.take(LABEL)}' $it sp") }
        }
    }

    private fun checkBounds(
        label: String,
        clickable: Boolean,
        b: Rect,
        s: Screen,
    ) {
        if (b.left < -1f || b.right > s.width + 1f) {
            s.add("poza ekranem", "'${label.take(LABEL)}' l=${b.left} r=${b.right} (ekran ${s.width})")
        }
        // Elementy przycięte dolną krawędzią (przewijana lista) mają widoczny tylko fragment: nie mierzymy.
        val clipped = b.bottom > s.height - CLIP_MARGIN_DP * s.density
        if (clickable && !clipped && b.height < MIN_TARGET_DP * s.density) {
            s.add("mały cel", "'${label.take(LABEL)}' ${b.height / s.density} dp wysokości, l=${b.left} t=${b.top}")
        }
    }

    /** Pary tekstów: nachodzące na siebie albo w tej samej linii z odstępem mniejszym niż 4 dp. */
    private fun checkNeighbours(s: Screen) {
        for (i in s.texts.indices) {
            for (j in i + 1 until s.texts.size) {
                val (ta, a) = s.texts[i]
                val (tb, b) = s.texts[j]
                val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                val pair = "'${ta.take(LABEL)}' i '${tb.take(LABEL)}'"
                when {
                    w > OVERLAP_PX && h > OVERLAP_PX -> s.add("teksty nachodzą", pair)
                    h > minOf(a.height, b.height) / 2 && w > -TOUCH_GAP_DP * s.density && w <= 0f ->
                        s.add("teksty stykają się", pair)
                }
            }
        }
    }

    /** Faktycznie zajęta szerokość tekstu (nie szerokość węzła z wagą): do pomiaru odstępu między sąsiadami. */
    private fun usedBounds(
        n: SemanticsNode,
        b: Rect,
    ): Rect {
        val layout = layoutOf(n) ?: return b
        val usedWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: return b
        return Rect(b.left, b.top, minOf(b.right, b.left + usedWidth), b.bottom)
    }

    private fun fontSizeSp(n: SemanticsNode): Float? {
        val size = layoutOf(n)?.layoutInput?.style?.fontSize ?: return null
        return if (size.isSp) size.value else null
    }

    private fun layoutOf(n: SemanticsNode): TextLayoutResult? {
        val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        action(results)
        return results.firstOrNull()
    }

    private const val LABEL = 40
}

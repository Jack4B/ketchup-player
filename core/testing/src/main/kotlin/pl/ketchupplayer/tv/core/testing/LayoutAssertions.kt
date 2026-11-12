package pl.ketchupplayer.tv.core.testing

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onRoot

/**
 * Sprawdza, że żaden element nie wychodzi poza ekran w poziomie (typowy błąd układu w pionie na telefonie).
 * Pionowo treść może wychodzić poza ekran, bo ekrany się przewijają.
 */
fun SemanticsNodeInteractionsProvider.assertNoHorizontalOverflow(tolerancePx: Float = 24f) {
    val width = onRoot().fetchSemanticsNode().size.width
    val offenders =
        onAllNodes(SemanticsMatcher("dowolny") { true })
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.right > width + tolerancePx || it.boundsInRoot.left < -tolerancePx }
            .map { "${it.id}: ${it.boundsInRoot}" }
    check(offenders.isEmpty()) { "Elementy poza ekranem (szerokość $width px):\n" + offenders.joinToString("\n") }
}

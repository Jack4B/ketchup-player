package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Próbnik motywu do galerii/podglądów: tło, panel, panel z focusem, kafel kanału, kropka nowych odcinków, akcenty. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ThemeSwatch(
    theme: ThemeId,
    modifier: Modifier = Modifier,
    accent: AccentId = theme.defaultAccent,
) {
    KetchupTheme(theme, accent) {
        val c = KetchupTheme.colors
        val shape = RoundedCornerShape(c.cornerRadius)
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(c.background)
                    .padding(horizontal = 48.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(theme.name, fontSize = 28.sp, color = c.textPrimary)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(c.surface2, shape)
                        .padding(16.dp),
            ) {
                Text("Panel (surface2)", fontSize = 20.sp, color = c.textPrimary)
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(c.surface3, shape)
                        .border(3.dp, c.accent, shape)
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Focus: surface3 + ring akcentu", fontSize = 20.sp, color = c.textPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(c.channelTile, RoundedCornerShape(8.dp)).padding(12.dp, 6.dp)) {
                    Text("JEDEN", fontSize = 18.sp, color = c.channelTileText)
                }
                Box(Modifier.size(12.dp).background(c.newEpisode, CircleShape))
                Text("nowe odcinki", fontSize = 18.sp, color = c.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(c.accent, RoundedCornerShape(24.dp)).padding(24.dp, 10.dp)) {
                    Text("Oglądaj", fontSize = 20.sp, color = c.onAccent)
                }
                AccentId.entries.forEach { a ->
                    Box(
                        Modifier.size(28.dp).background(
                            androidx.compose.ui.graphics
                                .Color(a.argb),
                            CircleShape,
                        ),
                    )
                }
            }
        }
    }
}

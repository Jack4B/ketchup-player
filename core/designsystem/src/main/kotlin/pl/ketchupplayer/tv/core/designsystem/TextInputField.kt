package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Pole tekstowe z etykietą: na TV OK otwiera klawiaturę ekranową, na telefonie dotyk. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TextInputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    val c = KetchupTheme.colors
    Column {
        Text(label, fontSize = 14.sp, color = c.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, c.textSecondary, RoundedCornerShape(c.cornerRadius.coerceAtMost(12.dp)))
                .padding(12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 20.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

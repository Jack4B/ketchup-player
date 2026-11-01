package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
    /** Ostatnie pole formularza: Enter/„Gotowe” wywołuje tę akcję (np. zapis), zamiast iść do następnego pola. */
    onDone: (() -> Unit)? = null,
) {
    val c = KetchupTheme.colors
    val focusManager = LocalFocusManager.current
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
                // Enter na klawiaturze ekranowej: kolejne pole albo, w ostatnim polu, zatwierdzenie formularza.
                // Na pilocie strzałka w dół w polu tekstowym trafia w klawiaturę, a nie w kolejny element.
                keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
                keyboardActions =
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = { onDone?.invoke() },
                    ),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 20.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

/** Pole szukania w kształcie pigułki z podpowiedzią (makieta v3); fokus podświetla obrys akcentem. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchField(
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val c = KetchupTheme.colors
    val focusManager = LocalFocusManager.current
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier
            .height(38.dp)
            .background(c.surface2, RoundedCornerShape(19.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) c.accent else c.outline, RoundedCornerShape(19.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = c.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        onDone()
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                ),
            textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

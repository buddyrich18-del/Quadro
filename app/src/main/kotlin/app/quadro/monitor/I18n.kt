package app.quadro.monitor

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import app.quadro.core.AppLanguage
import app.quadro.core.toEnglish
import java.util.Locale

/** Lingua corrente per i testi fuori dall'interfaccia (notifiche, avvisi in background). */
object I18n {
    @Volatile
    var english: Boolean = false

    fun resolve(lang: AppLanguage): Boolean = when (lang) {
        AppLanguage.IT -> false
        AppLanguage.EN -> true
        AppLanguage.SYSTEM -> Locale.getDefault().language != "it"
    }
}

val LocalEnglish = compositionLocalOf { false }

/** Traduce un testo italiano nella lingua scelta (fuori dalla composizione). */
fun tr(text: String): String = if (I18n.english) toEnglish(text) else text

/** Sceglie fra due testi già scritti nelle due lingue. */
fun pick(it: String, en: String): String = if (I18n.english) en else it

/**
 * Sostituisce il Text di Material nei file di questo pacchetto: traduce il testo
 * in inglese quando la lingua scelta lo è. I testi scritti dall'utente restano invariati
 * perché la traduzione tocca solo frasi note.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    val en = LocalEnglish.current
    androidx.compose.material3.Text(
        text = if (en) toEnglish(text) else text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        style = style,
    )
}

package pl.ketchupplayer.tv.core.common

/**
 * Znacznik wyjątku, którego `message` jest komunikatem przygotowanym do pokazania i zapisania (np. kod HTTP, nazwa
 * klasy błędu sieci, oczyszczony fragment odpowiedzi), a nie surowym tekstem biblioteki.
 */
interface SafeErrorMessage

/**
 * Wspólne, bezpieczne przygotowanie komunikatu błędu do UI, bazy i logów (issue #12). Surowy `message` ani
 * `toString()` nieznanego wyjątku nie powinien trafiać do użytkownika: biblioteki (np. OkHttp: „unexpected url:
 * …”) potrafią w nim zawrzeć cały adres z danymi dostępu.
 */
object ErrorText {
    const val MAX_LENGTH = 240

    /** Tekst po [Redactor.redact], przycięty do [MAX_LENGTH] (po maskowaniu, żeby ucięcie nie odsłoniło danych). */
    fun safe(
        raw: String?,
        fallback: String,
    ): String {
        val text = raw?.let { Redactor.redact(it).trim() }.orEmpty()
        return if (text.isEmpty()) fallback else text.take(MAX_LENGTH)
    }

    /**
     * Wyjątek oznaczony [SafeErrorMessage]: jego komunikat po [safe]. Każdy inny: komunikat ogólny z kodem w postaci
     * nazwy klasy (`"$fallback (IllegalArgumentException)"`), bez surowego tekstu.
     */
    fun of(
        t: Throwable?,
        fallback: String,
    ): String =
        when (t) {
            null -> fallback
            is SafeErrorMessage -> safe(t.message, fallback)
            else -> "$fallback (${className(t)})"
        }

    /** Nazwa klasy jako bezpieczny kod błędu. */
    fun className(t: Throwable): String = t.javaClass.simpleName.ifEmpty { t.javaClass.name.substringAfterLast('.') }
}

package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.common.ErrorText
import pl.ketchupplayer.tv.core.common.SafeErrorMessage
import pl.ketchupplayer.tv.provider.api.ProviderError

/** Wyjątek z komunikatem przygotowanym dla użytkownika (np. „Za mało pamięci…”); komunikat jest bezpieczny. */
internal class UserFacingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause),
    SafeErrorMessage

/**
 * Wspólne, bezpieczne przygotowanie komunikatu z wyjątku (issue #12). Znane nośniki bezpiecznych komunikatów
 * (błędy sieci, [ProviderError] z założenia bez URL-i, [UserFacingException]) zachowują tekst po maskowaniu; każdy
 * inny wyjątek daje komunikat ogólny z kodem w postaci nazwy klasy, a nie surowy `message` biblioteki.
 */
internal fun safeErrorText(
    t: Throwable?,
    fallback: String,
): String = if (t is ProviderError) ErrorText.safe(t.message, fallback) else ErrorText.of(t, fallback)

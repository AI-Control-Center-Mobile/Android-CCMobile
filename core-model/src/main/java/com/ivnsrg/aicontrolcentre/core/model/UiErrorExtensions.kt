package com.ivnsrg.aicontrolcentre.core.model

fun Throwable.toUiError(): UiError = when (this) {
    is UiException -> error
    else -> UiError.Unknown(message ?: "Неизвестная ошибка")
}

fun UiError.toReadableMessage(): String = when (this) {
    UiError.None -> ""
    UiError.MissingApiKey -> "OpenRouter API key не найден. Вернись в setup или settings."
    is UiError.Network -> message
    is UiError.Provider -> message
    is UiError.Unknown -> message
    is UiError.Validation -> message
}

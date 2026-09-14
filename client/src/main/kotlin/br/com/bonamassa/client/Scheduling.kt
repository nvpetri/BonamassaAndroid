package br.com.bonamassa.client

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Scheduling always uses the restaurant's timezone, regardless of device settings. */
fun scheduledTime(value: String?): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(requireNotNull(value)))
}.getOrDefault("próxima abertura")

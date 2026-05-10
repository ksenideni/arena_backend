package ru.mirea.robocompetition.archive

import kotlinx.serialization.json.Json

/** Общий Json-инстанс для сериализации payload-ов в JSONB. */
internal val ArchiveJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

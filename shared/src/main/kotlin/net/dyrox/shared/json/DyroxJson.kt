package net.dyrox.shared.json

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for third-party documents (Mojang, Fabric, Modrinth): unknown keys are ignored,
 * missing nullable fields become null, and nulls for fields with defaults fall back to the default.
 */
val DyroxJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Same settings, pretty-printed; used for files the user may open (configs, instance.json). */
val DyroxJsonPretty: Json = Json(DyroxJson) {
    prettyPrint = true
}

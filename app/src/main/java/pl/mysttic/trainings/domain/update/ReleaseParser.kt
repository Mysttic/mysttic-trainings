package pl.mysttic.trainings.domain.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Lista wydań z GitHuba przerobiona na [UpdateResult]. Osobno od warstwy sieci, bo to właśnie kształt
 * JSON-a najłatwiej zmienia się bez uprzedzenia — a tak da się to trzymać testem.
 *
 * Pytamy publiczne repozytorium strony, w którym leżą wydania wszystkich projektów Mysttic (D-33),
 * więc bierzemy wydania z tagiem [UpdateCheck.TAG_PREFIX] i gołym `X.Y.Z` po nim, a z nich najwyższą
 * wersję, nie pierwszą z listy: lista idzie za datą commita, na który wskazuje tag, a nie za kolejnością
 * wydań, a przerwane kopiowanie zostawia obok starsze wydanie (`RELEASES.md` strony). Lista bez naszego
 * wydania znaczy „nic nowego”, tak samo jak wydanie w tej samej wersji: to nie jest błąd sieci.
 */
object ReleaseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, currentVersion: String, fallbackPage: String): UpdateResult {
        val list = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
            ?: return UpdateResult(UpdateOutcome.FAILED)

        val ours = list
            .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .filter { it.flag("draft") != true && it.flag("prerelease") != true }
            .mapNotNull { release -> UpdateCheck.versionFromTag(release.text("tag_name"))?.let { it to release } }
            .maxWithOrNull { a, b -> UpdateCheck.compareVersions(a.first, b.first) }
            ?.second
            ?: return UpdateResult(UpdateOutcome.UP_TO_DATE)

        val tag = ours.text("tag_name")
        val outcome = UpdateCheck.decide(
            current = currentVersion,
            tag = tag,
            draft = ours.flag("draft") == true,
            prerelease = ours.flag("prerelease") == true,
        )
        if (outcome != UpdateOutcome.NEWER) return UpdateResult(outcome)

        val version = UpdateCheck.versionFromTag(tag).orEmpty()
        val wanted = UpdateCheck.assetNameFor(version)
        val asset = (ours["assets"] as? JsonArray)
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.firstOrNull { it.text("name") == wanted }
        if (asset != null && asset.text("state") != UpdateCheck.UPLOADED) return UpdateResult(UpdateOutcome.UP_TO_DATE)

        return UpdateResult(
            UpdateOutcome.NEWER,
            ReleaseInfo(
                version = version,
                notes = ours.text("body").orEmpty().trim(),
                pageUrl = fallbackPage,
                assetUrl = asset?.text("browser_download_url"),
                assetSize = asset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            ),
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    private fun JsonObject.flag(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
}

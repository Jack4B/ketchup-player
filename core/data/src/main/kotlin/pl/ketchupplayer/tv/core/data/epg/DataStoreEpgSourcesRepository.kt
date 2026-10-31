package pl.ketchupplayer.tv.core.data.epg

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.policy.EpgSourcePolicy
import java.util.UUID
import javax.inject.Inject

/**
 * Ustawienia źródeł EPG w DataStore. Flagi wbudowanych źródeł to osobne klucze Boolean;
 * własne źródła to lista JSON w jednym kluczu.
 * shortcut: JSON w preferencjach, przy Room (EpgSourceEntity, KONSPEKT §3.5) migrujemy w Fazie D.
 * Brak sekretów w URL-ach nie jest gwarantowany (xmltv.php?password=) — Faza D przenosi URL do SecretStore.
 */
class DataStoreEpgSourcesRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : EpgSourcesRepository {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class StoredCustom(
            val id: String,
            val name: String,
            val url: String,
            val enabled: Boolean = true,
            val providerId: String? = null,
        )

        override val sources: Flow<List<EpgSource>> =
            store.data.map { prefs ->
                listOf(
                    BuiltInEpgSources.ovh(prefs[OVH_ENABLED] ?: true),
                    BuiltInEpgSources.teleman(prefs[TELEMAN_ENABLED] ?: true),
                ) + decode(prefs[CUSTOM]).map { it.toSource() }
            }

        override suspend fun setEnabled(
            id: String,
            enabled: Boolean,
        ) {
            store.edit { prefs ->
                when (id) {
                    BuiltInEpgSources.OVH_ID -> prefs[OVH_ENABLED] = enabled
                    BuiltInEpgSources.TELEMAN_ID -> prefs[TELEMAN_ENABLED] = enabled
                    else -> {
                        val updated = decode(prefs[CUSTOM]).map { if (it.id == id) it.copy(enabled = enabled) else it }
                        prefs[CUSTOM] = encode(updated)
                    }
                }
            }
        }

        override suspend fun addCustom(
            name: String,
            url: String,
            providerId: ProviderId?,
        ): AddSourceResult {
            val cleanName = EpgSourcePolicy.validateName(name) ?: return AddSourceResult.INVALID_NAME
            val check =
                EpgSourcePolicy.validateUrl(url) as? EpgSourcePolicy.UrlCheck.Valid
                    ?: return AddSourceResult.INVALID_URL
            if (EpgSourcePolicy.isDuplicateUrl(sources.first(), check.normalized)) return AddSourceResult.DUPLICATE
            store.edit { prefs ->
                val entry =
                    StoredCustom(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        url = check.normalized,
                        providerId = providerId?.value,
                    )
                prefs[CUSTOM] = encode(decode(prefs[CUSTOM]) + entry)
            }
            return AddSourceResult.ADDED
        }

        override suspend fun removeCustom(id: String) {
            store.edit { prefs -> prefs[CUSTOM] = encode(decode(prefs[CUSTOM]).filterNot { it.id == id }) }
        }

        private fun decode(raw: String?): List<StoredCustom> =
            if (raw.isNullOrBlank()) emptyList() else json.decodeFromString(raw)

        private fun encode(list: List<StoredCustom>): String = json.encodeToString(list)

        private fun StoredCustom.toSource() =
            EpgSource(id, name, EpgSourceKind.CUSTOM_XMLTV, url, enabled, providerId?.let(::ProviderId))

        private companion object {
            val OVH_ENABLED = booleanPreferencesKey("epg_ovh_enabled")
            val TELEMAN_ENABLED = booleanPreferencesKey("epg_teleman_enabled")
            val CUSTOM = stringPreferencesKey("epg_custom_sources")
        }
    }

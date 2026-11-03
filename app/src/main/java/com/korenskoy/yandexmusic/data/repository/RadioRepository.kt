package com.korenskoy.yandexmusic.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.korenskoy.yandexmusic.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.radioDataStore: DataStore<Preferences> by preferencesDataStore(name = "radio_settings")

@Singleton
class RadioRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val FAVORITES_KEY = stringSetPreferencesKey("favorite_station_ids")

    val favoriteStationIds: Flow<Set<String>> = context.radioDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[FAVORITES_KEY] ?: emptySet()
        }

    // Radio is removed from this build: no stations
    fun getBuiltinStations(): List<RadioStation> = emptyList()

    suspend fun addFavorite(stationId: String) {
        context.radioDataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            newFavorites.add(stationId)
            prefs[FAVORITES_KEY] = newFavorites
        }
    }

    suspend fun removeFavorite(stationId: String) {
        context.radioDataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            newFavorites.remove(stationId)
            prefs[FAVORITES_KEY] = newFavorites
        }
    }

    suspend fun toggleFavorite(stationId: String) {
        context.radioDataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            if (newFavorites.contains(stationId)) {
                newFavorites.remove(stationId)
            } else {
                newFavorites.add(stationId)
            }
            prefs[FAVORITES_KEY] = newFavorites
        }
    }
}

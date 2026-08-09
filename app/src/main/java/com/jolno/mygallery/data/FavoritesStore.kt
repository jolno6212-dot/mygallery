package com.jolno.mygallery.data

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun isFavorite(id: Long): Boolean = prefs.getStringSet(KEY, emptySet())?.contains(id.toString()) ?: false

    fun toggle(id: Long) {
        val current = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())
        if (!current.add(id.toString())) {
            current.remove(id.toString())
        }
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun addAll(ids: Collection<Long>) {
        val current = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())
        current.addAll(ids.map { it.toString() })
        prefs.edit().putStringSet(KEY, current).apply()
    }

    companion object {
        private const val KEY = "favorite_ids"
    }
}

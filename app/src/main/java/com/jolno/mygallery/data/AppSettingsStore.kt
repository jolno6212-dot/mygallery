package com.jolno.mygallery.data

import android.content.Context

enum class SortField { NAME, DATE, SIZE }
enum class SortOrder { ASC, DESC }

data class SortOption(val field: SortField, val order: SortOrder)

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var folderColumns: Int
        get() = prefs.getInt(KEY_FOLDER_COLUMNS, 2)
        set(value) = prefs.edit().putInt(KEY_FOLDER_COLUMNS, value).apply()

    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 3)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value).apply()

    var defaultBucketId: String?
        get() = prefs.getString(KEY_DEFAULT_BUCKET, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_BUCKET, value).apply()

    var sortOption: SortOption
        get() = SortOption(
            field = SortField.entries.getOrElse(prefs.getInt(KEY_SORT_FIELD, SortField.DATE.ordinal)) { SortField.DATE },
            order = SortOrder.entries.getOrElse(prefs.getInt(KEY_SORT_ORDER, SortOrder.DESC.ordinal)) { SortOrder.DESC }
        )
        set(value) {
            prefs.edit()
                .putInt(KEY_SORT_FIELD, value.field.ordinal)
                .putInt(KEY_SORT_ORDER, value.order.ordinal)
                .apply()
        }

    companion object {
        private const val KEY_FOLDER_COLUMNS = "folder_columns"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_SORT_FIELD = "sort_field"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_DEFAULT_BUCKET = "default_bucket_id"
    }
}

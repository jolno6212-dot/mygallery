package com.jolno.mygallery.data

fun List<MediaFolder>.sortedFoldersBy(option: SortOption): List<MediaFolder> {
    val selector: (MediaFolder) -> Comparable<*> = when (option.field) {
        SortField.NAME -> { folder -> folder.name.lowercase() }
        SortField.DATE -> { folder -> folder.latestDateSec }
        SortField.SIZE -> { folder -> folder.itemCount }
    }
    @Suppress("UNCHECKED_CAST")
    val comparator = compareBy(selector as (MediaFolder) -> Comparable<Any>)
    return if (option.order == SortOrder.DESC) sortedWith(comparator.reversed()) else sortedWith(comparator)
}

fun List<MediaItem>.sortedItemsBy(option: SortOption): List<MediaItem> {
    val selector: (MediaItem) -> Comparable<*> = when (option.field) {
        SortField.NAME -> { item -> item.displayName.lowercase() }
        SortField.DATE -> { item -> item.dateAddedSec }
        SortField.SIZE -> { item -> item.size }
    }
    @Suppress("UNCHECKED_CAST")
    val comparator = compareBy(selector as (MediaItem) -> Comparable<Any>)
    return if (option.order == SortOrder.DESC) sortedWith(comparator.reversed()) else sortedWith(comparator)
}

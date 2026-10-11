package com.movierecommender.app.torrent

/** Pure, independently testable policy for picking a video inside untrusted torrent metadata. */
internal object TorrentFilePolicy {
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "m4v", "mov", "ts", "m2ts", "mpg", "mpeg", "wmv"
    )

    fun select(files: List<Pair<String, Long>>, requestedIndex: Int?): Int? {
        fun valid(index: Int): Boolean {
            if (index !in files.indices) return false
            val (name, size) = files[index]
            return size > 0L && name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
        }
        if (requestedIndex != null) return requestedIndex.takeIf(::valid)
        return files.indices.filter(::valid).maxByOrNull { files[it].second }
    }
}

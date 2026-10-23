package app.pixely.data.search

/**
 * What was typed, split into what to look for and what to keep out: "city at night, no people" is
 * "city at night" without "people". Embeddings ignore "no X" (the POC found "a photo with no dog"
 * still ranks dogs highly), so the exclusion is handled apart from the words.
 */
data class SearchQuery(val text: String, val without: String? = null) {

    /** The words to embed; a query that was only an exclusion looks for any photo. */
    val words: String get() = text.ifBlank { "a photo" }

    companion object {
        private val EXCLUSION = Regex("""(?i)(?:^|[\s,;]+)(?:with\s+)?(?:no|without)\s+([^,;]+)""")

        fun parse(input: String): SearchQuery {
            val match = EXCLUSION.find(input) ?: return SearchQuery(input.trim())
            val rest = input.removeRange(match.range).trim().trim(',', ';').trim()
            return SearchQuery(rest, match.groupValues[1].trim().ifBlank { null })
        }
    }
}

package com.example.estakada.util

object PhoneFormat {
    private val STORAGE_RE = Regex("""^\+7\d{10}$""")

    /** True if [phone] is already in storage format: +7XXXXXXXXXX */
    fun isStorage(phone: String): Boolean = STORAGE_RE.matches(phone)

    /**
     * Tries to normalise any phone string to storage format (+7XXXXXXXXXX).
     * Accepts:
     *   - 11 digits starting with 7 or 8  (e.g. 79528143808, 89528143808)
     *   - 10 digits                        (e.g. 9528143808)
     *   - Any of the above with separators (+, -, spaces, brackets)
     * Returns null if the digit count doesn't match.
     */
    fun normalize(raw: String): String? {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 11 && digits.startsWith("7") -> "+$digits"
            digits.length == 11 && digits.startsWith("8") -> "+7${digits.substring(1)}"
            digits.length == 10 -> "+7$digits"
            else -> null
        }
    }

    /**
     * Formats a storage-format phone to the display format:
     * +7 (XXX) XXX XX XX
     * If [storage] is not in storage format, returns it as-is.
     */
    fun toDisplay(storage: String): String {
        if (!isStorage(storage)) return storage
        val d = storage.substring(2) // 10 digits after "+7"
        return "+7 (${d.substring(0, 3)}) ${d.substring(3, 6)} ${d.substring(6, 8)} ${d.substring(8, 10)}"
    }
}

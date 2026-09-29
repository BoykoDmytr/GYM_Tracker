package com.boykodmytr.gymtracker.domain.transfer

/**
 * RFC 4180 CSV as spreadsheets actually write it: comma, semicolon (Excel with a Ukrainian or other
 * European locale) or tab separated, optional UTF-8 BOM, CRLF or LF line ends, quoted fields with
 * doubled quotes, separators and line breaks inside quotes.
 */
object Csv {
    const val BOM = '\uFEFF'
    private val CANDIDATE_DELIMITERS = charArrayOf(';', ',', '\t')

    /** Splits [text] into rows of fields. A trailing empty line is not a row. */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = if (text.startsWith(BOM)) 1 else 0

        fun endField() {
            row += field.toString()
            field.setLength(0)
            fieldStarted = false
        }

        fun endRow() {
            endField()
            rows += row
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when {
                    c == '"' && !fieldStarted && field.isEmpty() -> {
                        inQuotes = true
                        fieldStarted = true
                    }
                    c == delimiter -> endField()
                    c == '\r' -> {
                        endRow()
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    }
                    c == '\n' -> endRow()
                    else -> {
                        field.append(c)
                        fieldStarted = true
                    }
                }
            }
            i++
        }
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * Picks the separator that splits the first lines into the most consistent number of columns.
     * Quoted text is ignored, so commas inside notes do not vote for ",".
     */
    fun detectDelimiter(text: String): Char {
        val lines = logicalLines(text).filter { it.isNotBlank() }.take(30)
        if (lines.isEmpty()) return ';'
        return CANDIDATE_DELIMITERS.maxBy { delimiter ->
            val counts = lines.map { countOutsideQuotes(it, delimiter) }
            val typical = counts.groupingBy { it }.eachCount().filterKeys { it > 0 }.maxByOrNull { it.value }
            if (typical == null) 0 else typical.value * 1000 + typical.key
        }
    }

    /** One CSV line; fields are quoted only when needed so the file stays readable. */
    fun formatRow(fields: List<String>, delimiter: Char): String = fields.joinToString(delimiter.toString()) { quote(it, delimiter) }

    fun quote(value: String, delimiter: Char): String {
        val needsQuotes = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(' ') || value.endsWith(' ')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /**
     * Text that starts like a formula (=, +, @, or "-" before a letter) would be executed by Excel
     * and Sheets when the file is opened. A leading apostrophe makes it plain text; [unguardText]
     * removes it again on import, so notes survive the round trip unchanged.
     */
    fun guardText(value: String): String {
        if (value.isEmpty()) return value
        val first = value[0]
        val risky = first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r' ||
            first == '\'' && value.length > 1 && isRiskyStart(value.substring(1)) ||
            first == '-' && value.length > 1 && !value[1].isDigit() && !value[1].isWhitespace() && value[1] != '-'
        return if (risky) "'$value" else value
    }

    fun unguardText(value: String): String =
        if (value.length > 1 && value[0] == '\'' && isRiskyStart(value.substring(1))) value.substring(1) else value

    private fun isRiskyStart(value: String): Boolean = guardText(value) != value

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (c in line) {
            if (c == '"') inQuotes = !inQuotes else if (c == delimiter && !inQuotes) count++
        }
        return count
    }

    /** Lines of the file, keeping quoted line breaks inside their line. */
    private fun logicalLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in text.removePrefix(BOM.toString())) {
            if (c == '"') inQuotes = !inQuotes
            if ((c == '\n' || c == '\r') && !inQuotes) {
                if (current.isNotEmpty()) lines += current.toString()
                current.setLength(0)
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}

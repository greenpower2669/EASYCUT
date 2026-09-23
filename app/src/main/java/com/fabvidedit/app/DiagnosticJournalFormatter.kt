package com.fabvidedit.app

/** Pure presentation logic: the original diagnostic file is never rewritten to add HTML. */
internal object DiagnosticJournalFormatter {
    enum class Status { ERROR, OK, INFO, RECOVERED }

    data class Line(val text: String, val status: Status) {
        val label: String get() = when (status) {
            Status.ERROR -> "ERREUR"
            Status.OK -> "OK"
            Status.INFO, Status.RECOVERED -> "INFO"
        }
    }

    /** Preserve the log verbatim, adding a visible text label to each nonblank line. */
    fun classify(report: String): List<Line> {
        var previous = Status.INFO
        return report.split('\n').map { raw ->
            val line = raw.trimEnd('\r')
            if (line.isBlank()) {
                Line(line, Status.INFO)
            } else {
                val status = when {
                    line.startsWith("    at ") || line.startsWith("\tat ") ||
                        line.startsWith("Caused by:") || line.startsWith("Suppressed:") -> previous
                    else -> statusFor(line)
                }
                previous = status
                Line(line, status)
            }
        }
    }

    private fun statusFor(line: String): Status {
        val upper = line.uppercase()
        val noPreviousError = upper.contains("AUCUNE ERREUR CAPTURÉE") ||
            upper.contains("AUCUNE ERREUR CAPTUREE")
        val noPreviousExit = upper.contains("AUCUN ARRÊT ANDROID IDENTIFIÉ") ||
            upper.contains("AUCUN ARRET ANDROID IDENTIFIE")
        if (noPreviousError || noPreviousExit) return Status.INFO

        // A recovered optional failure is not a fatal error, even if the old log said ERROR.
        val recovered = listOf(
            "SCAN_SKIPPED", "SKIPPED_NATIVE", "OPTIONAL", "OPTIONNEL",
            "FACULTATIF", "IGNORÉ", "IGNORE", "FALLBACK", "REPLI",
            "RECOVERED", "RÉCUPÉRÉ", "RECUPERE", "NONFATAL", "NON_FATAL",
        ).any(upper::contains)
        val blocking = listOf(
            "UNCAUGHT", "FATAL", "CRASH", "IMPOSSIBLE", "BLOCKING",
            "BLOQUANT", "ANR", "LOW_MEMORY", "MANQUE DE MÉMOIRE",
        ).any(upper::contains)
        if (blocking) return Status.ERROR
        if (recovered) return Status.RECOVERED
        if (listOf("ERROR ", "ERREUR", "EXCEPTION", "TIMEOUT", "TIMED OUT",
                "ÉCHEC", "ECHEC", "FAILED", "FAILURE",
            ).any(upper::contains)
        ) return Status.ERROR
        if (upper.startsWith("EASYCUT ") || upper.contains(" SUCCESS") ||
            upper.contains(" SUCCÈS") || upper.contains(" SUCCES") ||
            upper.contains(" IMPORT_OK") || upper.contains(" EXPORT_OK")
        ) return Status.OK
        return Status.INFO
    }

    /** Literal HTML markers in the *copied text only*: legible even in plain-text AI chat. */
    fun forCopy(report: String): String = buildString {
        append("<div class=\"easycut-get-err\">\n")
        for (line in classify(report)) {
            if (line.text.isBlank()) {
                append("<br>\n")
            } else {
                val color = when (line.status) {
                    Status.ERROR -> "#D32F2F"
                    Status.OK, Status.INFO -> "#16803D"
                    Status.RECOVERED -> "#737D8C"
                }
                append("<span style=\"color:")
                append(color)
                append("\"><b>[")
                append(line.label)
                append("]</b> ")
                append(escapeHtml(line.text))
                append("</span><br>\n")
            }
        }
        append("</div>")
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(when (ch) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> ch.toString()
            })
        }
    }
}

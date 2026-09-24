package com.fabvidedit.app.media

/** Short inventory only: put JSON in a dedicated app-private file, never FFmpegKit logs. */
internal object FfprobeInventoryCommand {
    fun stdoutArguments(input: String): Array<String> {
        require(input.isNotBlank()) { "Chemin FFprobe manquant" }
        return arrayOf("-v", "error", "-print_format", "json",
            "-show_format", "-show_streams", input)
    }

    /** Bounded output, deeper probe only after both short inventory routes fail.
     * This inspects stream headers, NOT decoded frames; do not enable -show_frames here.
     */
    fun expandedStdoutArguments(input: String): Array<String> {
        require(input.isNotBlank()) { "Chemin FFprobe manquant" }
        return arrayOf(
            "-v", "error", "-probesize", "50000000",
            "-analyzeduration", "30000000", "-fflags", "+genpts",
            "-print_format", "json", "-show_format", "-show_streams", input,
        )
    }

    fun arguments(input: String, output: String): Array<String> {
        require(input.isNotBlank() && output.isNotBlank()) { "Chemin FFprobe manquant" }
        return arrayOf(
            "-v", "error",
            "-print_format", "json",
            "-show_format",
            "-show_streams",
            "-o", output,
            input,
        )
    }
}

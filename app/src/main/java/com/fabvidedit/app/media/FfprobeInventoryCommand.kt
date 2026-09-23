package com.fabvidedit.app.media

/** Short inventory only: put JSON in a dedicated app-private file, never FFmpegKit logs. */
internal object FfprobeInventoryCommand {
    fun stdoutArguments(input: String): Array<String> {
        require(input.isNotBlank()) { "Chemin FFprobe manquant" }
        return arrayOf("-v", "error", "-print_format", "json",
            "-show_format", "-show_streams", input)
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

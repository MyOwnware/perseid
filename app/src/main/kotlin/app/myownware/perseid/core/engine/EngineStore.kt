package app.myownware.perseid.core.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal data class EngineSelection(
    val current: String,
    val previous: String?,
    val bundleSeen: String,
)

internal class EngineStore(
    private val directory: File,
    private val executable: File,
) {
    private val selectionFile = File(directory, "selection")

    fun selection(): EngineSelection? {
        if (!selectionFile.exists()) return null
        val (current, previous, bundleSeen) = selectionFile.readLines()
        return EngineSelection(current, previous.ifEmpty { null }, bundleSeen)
    }

    fun save(
        version: String,
        input: InputStream,
    ) {
        directory.mkdirs()
        val destination = archive(version)
        withTemporaryFile(directory) { staged ->
            staged.outputStream().use { input.copyTo(it) }
            replace(staged, destination)
        }
    }

    fun select(selection: EngineSelection) {
        val old = selection()
        directory.mkdirs()
        withTemporaryFile(directory) { staged ->
            val fields = listOf(selection.current, selection.previous.orEmpty(), selection.bundleSeen)
            staged.writeText(fields.joinToString("\n"))
            sync(selection)
            try {
                replace(staged, selectionFile)
            } catch (failure: IOException) {
                if (old != null) sync(old)
                throw failure
            }
        }
        val retained = setOfNotNull(archive(selection.current).name, selection.previous?.let(::archive)?.name)
        directory
            .listFiles()
            ?.filter { it.name.startsWith(ARCHIVE_PREFIX) && it.name !in retained }
            ?.forEach { it.delete() }
    }

    fun sync(selection: EngineSelection) {
        val source = archive(selection.current)
        if (!source.isFile) throw IOException("Missing installed engine")
        if (executable.exists() && Files.isSameFile(executable.toPath(), source.toPath())) return
        withTemporaryFile(executable.parentFile!!) { staged ->
            Files.delete(staged.toPath())
            Files.createSymbolicLink(staged.toPath(), source.toPath())
            replace(staged, executable)
        }
    }

    fun restore(): EngineSelection {
        val active = requireNotNull(selection())
        val previous = active.previous ?: return active
        return active.copy(current = previous, previous = active.current).also(::select)
    }

    private fun archive(version: String): File {
        require(isEngineVersion(version))
        return File(directory, "$ARCHIVE_PREFIX$version")
    }
}

private inline fun withTemporaryFile(
    directory: File,
    action: (File) -> Unit,
) {
    val temporary = File.createTempFile("pending-", ".tmp", directory)
    try {
        action(temporary)
    } finally {
        temporary.delete()
    }
}

private fun replace(
    source: File,
    destination: File,
) {
    Files.move(source.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}

private const val ARCHIVE_PREFIX = "release-"

package de.gebetszeiten.desktop

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Datenordner (Einstellungen, Cache, Lock, Protokoll): Systemeigenschaft
 * `gebetszeiten.dataDir`, sonst Umgebungsvariable `GEBETSZEITEN_DATA_DIR`,
 * sonst `%APPDATA%\Gebetszeiten`. Leere Werte zaehlen nicht.
 */
fun resolveDataDir(property: String?, environment: String?, appData: String?): Path {
    property?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
    environment?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
    val base = appData?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
    return Path.of(base, "Gebetszeiten")
}

/**
 * Einzelinstanz ueber eine Lock-Datei `lock` im Datenordner. Die Sperre haelt
 * Windows, solange der Prozess lebt; nach einem Absturz ist sie von selbst weg.
 */
class SingleInstance private constructor(private val channel: FileChannel, private val lock: FileLock) {

    fun release() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        /** Die Sperre, oder null, wenn schon eine andere Instanz laeuft. */
        fun tryAcquire(dir: Path): SingleInstance? {
            Files.createDirectories(dir)
            val channel = FileChannel.open(dir.resolve("lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null // dieselbe JVM haelt sie schon
            } catch (e: IOException) {
                null
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstance(channel, lock)
        }
    }
}

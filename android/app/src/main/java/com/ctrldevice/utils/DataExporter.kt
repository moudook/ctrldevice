package com.ctrldevice.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Handles exporting app data (logs, memory, skills) to a single ZIP file.
 */
object DataExporter {

    fun exportUserData(context: Context): File? {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val zipFile = File(exportDir, "ctrldevice_export_${System.currentTimeMillis()}.zip")
            val fos = FileOutputStream(zipFile)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            // 1. Export Internal Files (agent_memory.json, skills, etc.)
            val internalFiles = context.filesDir.listFiles()
            internalFiles?.forEach { file ->
                addToZip(file, file.name, zos)
            }

            // 2. Export Skills/Macros (if stored separately)
            // Currently SkillRegistry loads from assets but saves to local storage?
            // If they are in filesDir, they are covered above.

            zos.close()
            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun addToZip(file: File, fileName: String, zos: ZipOutputStream) {
        if (file.isHidden) return

        if (file.isDirectory) {
            val children = file.listFiles()
            children?.forEach { child ->
                addToZip(child, "$fileName/${child.name}", zos)
            }
            return
        }

        val fis = FileInputStream(file)
        val bis = BufferedInputStream(fis)
        val zipEntry = ZipEntry(fileName)
        zos.putNextEntry(zipEntry)

        val bytes = ByteArray(1024)
        var length: Int
        while (bis.read(bytes).also { length = it } >= 0) {
            zos.write(bytes, 0, length)
        }

        zos.closeEntry()
        bis.close()
        fis.close()
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}

package dev.teogor.querent.common

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Location
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class KSVirtualFile(val baseDir: File, val name: String) : KSFile {
    override val annotations: Sequence<KSAnnotation>
        get() = throw Exception("$name should not be used.")

    override val declarations: Sequence<KSDeclaration>
        get() = throw Exception("$name should not be used.")

    override val fileName: String
        get() = "<$name is a virtual file; DO NOT USE.>"

    override val filePath: String
        get() = File(baseDir, fileName).path

    override val packageName: KSName
        get() = throw Exception("$name should not be used.")

    override val origin: Origin
        get() = throw Exception("$name should not be used.")

    override val location: Location
        get() = throw Exception("$name should not be used.")

    override val parent: KSNode?
        get() = throw Exception("$name should not be used.")

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        throw Exception("$name should not be used.")
    }
}

/**
 * Used when an output potentially depends on new information.
 */
class AnyChanges(baseDir: File) : KSVirtualFile(baseDir, "AnyChanges")

/**
 * Used for classes from classpath, i.e., classes without source files.
 */
class NoSourceFile(baseDir: File, val fqn: String) : KSVirtualFile(baseDir, "NoSourceFile for $fqn")

// Copy recursively, including last-modified-time of file and its parent dirs.
//
// `java.nio.file.Files.copy(path1, path2, options...)` keeps last-modified-time (if supported) according to
// https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html
fun copyWithTimestamp(src: File, dst: File, overwrite: Boolean) {
    if (!dst.parentFile.exists())
        copyWithTimestamp(src.parentFile, dst.parentFile, false)
    if (overwrite) {
        Files.copy(
            src.toPath(),
            dst.toPath(),
            StandardCopyOption.COPY_ATTRIBUTES,
            StandardCopyOption.REPLACE_EXISTING
        )
    } else {
        Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
    }
}

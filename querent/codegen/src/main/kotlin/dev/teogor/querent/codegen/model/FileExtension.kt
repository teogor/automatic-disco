package dev.teogor.querent.codegen.model

sealed interface FileExtension {
  val extension: String

  data object Kt : FileExtension {
    override val extension: String = "kt"
  }

  data object Class : FileExtension {
    override val extension: String = "class"
  }

  data object Java : FileExtension {
    override val extension: String = "java"
  }

  data object Xml : FileExtension {
    override val extension: String = "xml"
  }

  data object Json : FileExtension {
    override val extension: String = "json"
  }

  class Custom(
    override val extension: String,
  ) : FileExtension
}

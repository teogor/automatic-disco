fun String.toBooleanEnv(): Boolean {
  val envValue = System.getenv(this) ?: ""
  return envValue.lowercase() == "true"
}

fun String.toStringEnv(): String {
  val envValue = System.getenv(this) ?: ""
  return envValue.lowercase()
}

fun isVirtualEnvironment(): Boolean {
  return "CI".toBooleanEnv() || "CONDA".toBooleanEnv()
}

fun isProductionEnvironment(): Boolean {
  return "PRODUCTION_ENV".toBooleanEnv()
}

println("isVirtualEnvironment - ${isVirtualEnvironment()}")
println("isProductionEnvironment - ${isProductionEnvironment()}")

val mavenLocalRepoPath = "${System.getProperty("user.home")}/.m2/repository"
println("Maven local repository path: $mavenLocalRepoPath")

val mavenLocalRepoDir = File(mavenLocalRepoPath)
if (mavenLocalRepoDir.exists() && mavenLocalRepoDir.isDirectory) {
  println("Maven local repository contents:")
  mavenLocalRepoDir.listFiles()?.forEach {
    println(it.name)
  }
} else {
  println("Maven local repository directory not found or is not a directory.")
}

pluginManagement {
  includeBuild("querent")

  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
  }
}

rootProject.name = "querent-root"

include("android-app")

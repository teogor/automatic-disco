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
    if (!isProductionEnvironment()) {
      mavenLocal()
    }
  }
}

rootProject.name = "querent-root"

include("android-app")

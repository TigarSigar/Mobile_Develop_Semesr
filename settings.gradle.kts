pluginManagement {
    repositories {
        // Официальные репозитории должны быть первыми
        google()
        mavenCentral()
        gradlePluginPortal()

        // Зеркала как запасной вариант (без ограничений по контенту)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Semestr"
include(":app")
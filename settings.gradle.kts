pluginManagement {
    repositories {
        google()  // Google 리포지토리 추가
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // PREFER_SETTINGS로 설정
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "My Application"
include(":app")

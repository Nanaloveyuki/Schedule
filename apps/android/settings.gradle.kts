pluginManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            google()
            mavenCentral()
            gradlePluginPortal()
            maven("https://repo.huaweicloud.com/repository/maven/")
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        } else {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://repo.huaweicloud.com/repository/maven/")
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            google()
            mavenCentral()
            maven("https://repo.huaweicloud.com/repository/maven/")
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/public")
        } else {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://repo.huaweicloud.com/repository/maven/")
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "schedule-android"
include(":app")


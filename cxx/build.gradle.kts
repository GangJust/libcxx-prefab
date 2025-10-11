plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    id("signing")
}

android {
    namespace = "dev.rikka.ndk.thirdparty.libcxx"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 21

        externalNativeBuild {
            ndkBuild {
                arguments += "-j${Runtime.getRuntime().availableProcessors()}"
            }
        }
    }

    androidResources {
        enable = false
    }

    buildFeatures {
        prefabPublishing = true
        buildConfig = false
    }

    externalNativeBuild {
        ndkBuild {
            path("jni/Android.mk")
        }
    }

    prefab {
        register("cxx") {
            headers = "$rootDir/cxx-source/include"
        }
    }
}

// NDK does not run strip on static libraries, do it ourselves
open class ExecOperationsTask @Inject constructor(@Internal val execOperations: ExecOperations) : DefaultTask()

val buildDirPath: File = project.layout.buildDirectory.asFile.get()
val stripNativeRelease = tasks.register<ExecOperationsTask>("stripNativeRelease") {
    doLast {
        val libcxxTree = fileTree(
            mapOf(
                "dir" to buildDirPath.resolve("intermediates/cxx"),
                "include" to listOf("**/Release/**/*.a")
            )
        )
        if (libcxxTree.isEmpty) {
            println("No static libraries found to strip.")
            return@doLast
        }

        val stripTree = fileTree(
            mapOf(
                "dir" to android.ndkDirectory.resolve("toolchains/llvm/prebuilt"),
                "include" to listOf("**/llvm-objcopy.exe", "**/llvm-objcopy")
            )
        )
        val strip = stripTree.firstOrNull()
        if (strip == null) {
            println("No strip tool found.")
            return@doLast
        }

        libcxxTree.forEach { libcxx ->
            println("Stripping $libcxx")
            execOperations.exec {
                commandLine(listOf(strip, "--strip-unneeded", "--remove-section=.comment", libcxx))
                workingDir(libcxx.parent)
                isIgnoreExitValue = false
            }
        }
    }
}

tasks.whenTaskAdded {
    if (name == "externalNativeBuildRelease") {
        finalizedBy(stripNativeRelease)
    }
}

mavenPublishing {
    coordinates("dev.rikka.ndk.thirdparty", "libcxx", "1.3.0")

    pom {
        name.set("libcxx Prefab")
        description.set("LLVM libc++, specifically for Android, removing exception and RTTI support (https://github.com/topjohnwu/libcxx).")
        url.set("https://github.com/RikkaW/libcxx-prefab")
        licenses {
            license {
                name.set("Apache License v2.0")
                url.set("https://github.com/topjohnwu/libcxx/blob/master/LICENSE.TXT")
                distribution.set("https://github.com/topjohnwu/libcxx/blob/master/LICENSE.TXT")
            }
        }
        developers {
            developer {
                name.set("rikka")
                url.set("https://github.com/RikkaW/libcxx-prefab")
            }
        }
        scm {
            url.set("https://github.com/RikkaW/libcxx-prefab")
            connection.set("scm:git@github.com:RikkaW/libcxx-prefab.git")
            developerConnection.set("scm:git@github.com:RikkaW/libcxx-prefab.git")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}
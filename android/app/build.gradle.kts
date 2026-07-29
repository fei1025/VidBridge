import java.io.File

val releaseKeystorePath = providers.gradleProperty("vidbridge.release.storeFile")
    .orElse(providers.environmentVariable("VIDBRIDGE_KEYSTORE"))
    .orNull
val releaseKeystorePassword = providers.gradleProperty("vidbridge.release.storePassword")
    .orElse(providers.environmentVariable("VIDBRIDGE_KEYSTORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("vidbridge.release.keyAlias")
    .orElse(providers.environmentVariable("VIDBRIDGE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("vidbridge.release.keyPassword")
    .orElse(providers.environmentVariable("VIDBRIDGE_KEY_PASSWORD"))
    .orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.vidbridge"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.vidbridge"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("configuredRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("configuredRelease")
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    packaging.jniLibs {
        useLegacyPackaging = false
        pickFirsts += "**/libc++_shared.so"
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

// libvlc-all 3.6.3 carries a 4 KB libc++_shared.so. Replace only that runtime
// library with the NDK r28 build so the final APK also passes ELF 16 KB checks.
val alignedLibcxxDir = layout.buildDirectory.dir("generated/16kb-jniLibs").get().asFile
val copy16KbLibcxx = tasks.register<Copy>("copy16KbLibcxx") {
    val ndkRoot = File(android.sdkDirectory, "ndk/${android.ndkVersion}")
    val sysroot = File(ndkRoot, "toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib")
    mapOf(
        "aarch64-linux-android" to "arm64-v8a",
        "arm-linux-androideabi" to "armeabi-v7a",
        "i686-linux-android" to "x86",
        "x86_64-linux-android" to "x86_64",
    ).forEach { (triple, abi) ->
        from(File(sysroot, "$triple/libc++_shared.so")) { into(abi) }
    }
    into(alignedLibcxxDir)
}
android.sourceSets.getByName("main").jniLibs.srcDir(alignedLibcxxDir)
tasks.named("preBuild") { dependsOn(copy16KbLibcxx) }

listOf("Debug", "Release").forEach { variant ->
    tasks.matching { it.name == "merge${variant}NativeLibs" }.configureEach {
        outputs.upToDateWhen { false }
        doLast {
            val mergedLibDir = File(
                layout.buildDirectory.get().asFile,
                "intermediates/merged_native_libs/${variant.lowercase()}/merge${variant}NativeLibs/out/lib",
            )
            project.copy {
                from(alignedLibcxxDir)
                into(mergedLibDir)
            }
        }
    }
}

// Room 2.8.4 migration bundles are generated against serialization 1.8.x;
// Compose's test graph otherwise downgrades the AndroidTest runtime to 1.7.x.
configurations.matching { it.name.contains("AndroidTest", ignoreCase = true) }.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.videolan.android:libvlc-all:3.6.3")
    implementation("com.hierynomus:smbj:0.14.0")
    implementation("org.codelibs:jcifs:2.1.40")
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("com.emc.ecs:nfs-client:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

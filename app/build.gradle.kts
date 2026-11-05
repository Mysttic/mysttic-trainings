import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Numer wersji ma jedno źródło: VERSION.md w korzeniu repo. Gradle czyta stamtąd `versionName`,
// a `versionCode` wylicza z tego samego numeru, więc nie ma czego synchronizować ręcznie.
val appVersion: String = rootProject.file("VERSION.md").readLines()
    .map { it.trim() }
    .firstOrNull { line ->
        line.split(".").let { parts -> parts.size == 3 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) } }
    }
    ?: error("VERSION.md: brak linii z samym numerem wersji (format 1.2.3)")

// major * 10000 + minor * 100 + patch: rośnie z każdym podniesieniem numeru, dopóki minor i patch < 100.
val appVersionCode: Int = appVersion.split(".").let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

// Podpis release: ścieżka do keystore z env/gradle.properties (CI ustawia env z Secrets).
// Bez keystore `assembleRelease` daje APK niepodpisany (app-release-unsigned.apk) — do testów i tak używaj debug.
val releaseKeystorePath: String? = System.getenv("TRAININGS_KEYSTORE_PATH")
    ?: (project.findProperty("trainings.keystore.path") as String?)
val hasReleaseKeystore = releaseKeystorePath != null && file(releaseKeystorePath).exists()

android {
    namespace = "pl.mysttic.trainings"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.mysttic.trainings"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("TRAININGS_KEYSTORE_PASSWORD")
                    ?: (project.findProperty("trainings.keystore.password") as String?)
                keyAlias = System.getenv("TRAININGS_KEY_ALIAS")
                    ?: (project.findProperty("trainings.key.alias") as String?)
                keyPassword = System.getenv("TRAININGS_KEY_PASSWORD")
                    ?: (project.findProperty("trainings.key.password") as String?)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("17")
        }
    }

    buildFeatures {
        compose = true
        // Aktualizator czyta z BuildConfig numer wersji, którą właśnie zainstalowano.
        buildConfig = true
    }

    packaging.resources {
        excludes += "/META-INF/AL2.0"
        excludes += "/META-INF/LGPL2.1"
    }

    // Baza ćwiczeń (D-47) leży poza modułem, w `baza/` w korzeniu repo, i ma własny numer i własne wydania.
    // Ten sam katalog jedzie do APK jako assety (`exercises.json`, `images/<id>.jpg`) i do ZIP-a z wydania bazy,
    // więc w `baza/` są wyłącznie dane: wszystko, co tam leży, trafia do aplikacji.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("baza"))

    testOptions.unitTests {
        // Robolectric rysuje prawdziwe ekrany w testach jednostkowych, więc potrzebuje zasobów aplikacji (D-40).
        isIncludeAndroidResources = true
        all {
            // Gdzie lądują zrzuty: `-Proborazzi.record` zapisuje wzorce, bez niego test tylko rysuje.
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.record")?.toString() ?: "false")
            it.systemProperty("roborazzi.test.verify", project.findProperty("roborazzi.verify")?.toString() ?: "false")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    // Zrzuty ekranu palet bez emulatora: Robolectric rysuje natywnie, Roborazzi zapisuje PNG (D-40).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

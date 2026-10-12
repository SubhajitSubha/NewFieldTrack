plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.fieldtrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fieldtrack"
        minSdk = 26  // Lowered for broader device support
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "ENVIRONMENT", "\"prod\"")
        buildConfigField("String", "AUTH_PROVIDER", "\"FIREBASE\"")
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            // signingConfig removed: debug.keystore is not in the repo.
            // AGP falls back to the standard ~/.android/debug.keystore.
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // Room writes the schema JSON here on every build. Committing it is what makes a
    // migration reviewable and testable against the real previous version.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Rules live in app/lint.xml. Note that Android's own HardcodedText check
        // only inspects XML layouts — it is blind to Compose. The real enforcement
        // for Compose is the checkComposeStrings task defined below.
        lintConfig = file("lint.xml")
        abortOnError = true
        checkDependencies = false
    }
}

/**
 * Fails the build on a user-visible string literal in Compose code.
 *
 * This exists because Android lint's HardcodedText rule predates Compose and only
 * reads XML layouts, so `Text("Start work")` sails straight past it. Without a check
 * of our own, "all strings go in strings.xml" is a convention that decays the first
 * time someone is in a hurry — and the Hindi release then becomes a refactor instead
 * of a translation.
 *
 * Scope is the rewritten code only. The legacy screens under ui/ are full of literals
 * and are being replaced flow by flow; failing on code already scheduled for deletion
 * would just train people to disable the check.
 */
val checkComposeStrings = tasks.register("checkComposeStrings") {
    group = "verification"
    description = "Fails if Compose code outside the legacy packages hardcodes user-visible text."

    val sourceRoot = file("src/main/java/com/example/fieldtrack")
    val scanned = listOf("designsystem", "feature")
    // The component catalogue is a developer tool rendered only in Android Studio
    // previews. Its copy never reaches a user, so translating it would be noise.
    val exempt = listOf("designsystem/catalog")

    inputs.dir(sourceRoot).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { false }

    doLast {
        // A literal holding two or more consecutive letters, in a slot that renders.
        //
        // `Text(` is anchored to a word boundary so it matches the composable and not
        // the tail of some other identifier. Without that, `ClipData.newPlainText(
        // "code", ...)` reads as a hardcoded `Text("code")` and the build fails on a
        // clipboard label no user will ever see — a false alarm that teaches people
        // to reach for the off switch.
        val offenders = Regex(
            """(?:(?<![A-Za-z0-9_])Text\(\s*|(?:text|label|title|subtitle|description|placeholder|helper|contentDescription|unit)\s*=\s*)"[^"\n]*[A-Za-z]{2,}[^"\n]*""""
        )
        val problems = mutableListOf<String>()

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { candidate ->
                val path = candidate.relativeTo(sourceRoot).invariantSeparatorsPath
                scanned.any { path.startsWith(it) } && exempt.none { path.startsWith(it) }
            }
            .forEach { candidate ->
                candidate.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("//")) return@forEachIndexed
                    offenders.find(line)?.let { match ->
                        problems += "${candidate.relativeTo(rootDir)}:${index + 1}  ${match.value.trim()}"
                    }
                }
            }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Hardcoded user-visible text found in Compose code.")
                    appendLine("Move each of these into res/values/strings.xml and read it with stringResource().")
                    appendLine()
                    problems.forEach { appendLine("  $it") }
                }
            )
        }
    }
}

tasks.named("check") { dependsOn(checkComposeStrings) }

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    ksp(libs.androidx.room.compiler)

    // Firebase (BOM manages versions)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
//    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.analytics)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testing.
    // Robolectric lets the Room tests run on the JVM: `./gradlew test` exercises the
    // real database and real SQL without an emulator, so the data layer is verified
    // on every build rather than by hand.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
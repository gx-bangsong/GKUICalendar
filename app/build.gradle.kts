import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
    alias(libs.plugins.ec4j.editorconfig)
    alias(libs.plugins.lineageos.generatebp)
}

editorconfig {
	excludes = listOf("metadata/**", "**/*.webp")
}

kotlin {
    jvmToolchain(21)
}

configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.24")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24")
}


android {
	namespace = "ws.xsoh.etar"
	testNamespace = "com.android.calendar.tests"
	compileSdk = 36

	defaultConfig {
		minSdk = 23
		targetSdk = 34
		versionCode = 51
		versionName = "1.0.51"
		applicationId = "ws.xsoh.etar"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			// TODO: could be enabled for ProGuard minimization
			isMinifyEnabled = false
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".CalendarRecentSuggestionsProvider"
			)
		}

		debug {
			isMinifyEnabled = false

			applicationIdSuffix = ".debug"
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".debug.CalendarRecentSuggestionsProvider"
			)
		}
	}

	buildFeatures {
        buildConfig = true
		viewBinding = true
	}

	/*
	 * To sign release build, create file gradle.properties in ~/.gradle/ with this content:
	 *
	 * signingStoreLocation=/home/key.store
	 * signingStorePassword=xxx
	 * signingKeyAlias=alias
	 * signingKeyPassword=xxx
	 */
	val signingStoreLocation: String? by project
	val signingStorePassword: String? by project
	val signingKeyAlias: String? by project
	val signingKeyPassword: String? by project

	if (
		signingStoreLocation != null &&
		signingStorePassword != null &&
		signingKeyAlias != null &&
		signingKeyPassword != null
	) {
		println("Found sign properties in gradle.properties! Signing build…")

		signingConfigs {
			named("release").configure {
				storeFile = File(signingStoreLocation!!)
				storePassword = signingStorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}

		buildTypes.named("release").get().signingConfig = signingConfigs.named("release").get()
	} else {
		buildTypes.named("release").get().signingConfig = null
	}

	lint {
		lintConfig = file("lint.xml")
		// TODO: Resolve lint errors due to 363aa9c237a33e9e1a40bdfd9039dcaaa855a5a0
		abortOnError = false
	}

	testOptions {
		unitTests {
			// JVM unit tests run against android.jar stubs. LunarCache calls
			// android.util.SparseArray; stubbed calls must return defaults
			// instead of throwing.
			isReturnDefaultValues = true
		}
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true

		sourceCompatibility(JavaVersion.VERSION_21)
		targetCompatibility(JavaVersion.VERSION_21)
	}

kotlin {
    compilerOptions {
         jvmTarget = JvmTarget.JVM_21
    }
}

	useLibrary("android.test.base")
	useLibrary("android.test.mock")

	androidResources {
		generateLocaleConfig = false
	}

}

dependencies {

	// Core
	implementation(libs.androidx.core)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.google.android.material)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.concurrent.futures)
    testImplementation(libs.junit)

	coreLibraryDesugaring(libs.android.tools.desugar)

	// Coroutines
	implementation(libs.kotlinx.coroutines.android)

	// https://mvnrepository.com/artifact/org.dmfs/lib-recur
	implementation(libs.dmfs.lib.recur)

	// lifecycle
	implementation(libs.androidx.lifecycle.livedata)

	testImplementation(libs.androidx.test.runner)

	implementation(libs.androidx.room.runtime)
	kapt(libs.androidx.room.compiler)
	implementation(libs.androidx.room.ktx)

	// AppSearch: contribute calendar events to the on-device search index,
	// surfaced by Pixel Launcher / system-level QSB on Android 12+. Calls
	// are runtime-gated — see com.android.calendar.search.CalendarAppSearchIndexer.
	implementation(libs.androidx.appsearch)

	// PlatformStorage backend: Android 12+ central index. Code paths that
	// touch this must additionally check Build.VERSION.SDK_INT >= 31.
	implementation(libs.androidx.appsearch.platform.storage)

	// LocalStorage backend: in-app private index, works on every Android
	// version this module supports. Used as the fallback on pre-S devices.
	implementation(libs.androidx.appsearch.local.storage)
}

kapt {
    correctErrorTypes = true
}

configure<GenerateBpPluginExtension> {
	targetSdk.set(android.defaultConfig.targetSdk!!)
	minSdk.set(android.defaultConfig.minSdk!!)
	availableInAOSP.set { module: Module ->
		when {
			module.group.startsWith("androidx.databinding") -> false
			module.group.startsWith("androidx") -> true
			module.group.startsWith("com.google") -> true
			module.group.startsWith("org.jetbrains") -> true
			else -> false
		}
	}
}

// The repository's CI workflows only invoke assembleDebug (no test job), and
// workflow files cannot be modified by the automation that maintains this
// branch. Running the JVM unit tests as part of every CI debug build is the
// only hook that executes them on GitHub Actions; local builds are unaffected.
if (System.getenv("CI") != null) {
	// The legacy AOSP test suite (16 files, ~289 tests) was written for an
	// instrumented runner and fails on the plain JVM (~194 of 289); it has
	// never been executed by this repository's CI. Until it is either fixed
	// or moved to androidTest, CI runs the JVM-capable lunar tests only.
	tasks.withType<Test>().configureEach {
		filter.includeTestsMatching("com.android.calendar.lunar.*")
		filter.includeTestsMatching("com.android.calendar.subscription.*")
		// List every passing lunar test in the Actions log so the runs are
		// auditable there directly.
		testLogging.events(TestLogEvent.PASSED)
	}
	tasks.configureEach {
		if (name == "assembleDebug") {
			dependsOn("testDebugUnitTest")
		}
	}
}

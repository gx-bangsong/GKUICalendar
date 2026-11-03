import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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


// LineageOS built-in package (see upstream AndroidManifest package and src_aosp BuildConfig).
// namespace stays ws.xsoh.etar, so R and BuildConfig keep their packages.
val baseApplicationId = "org.lineageos.etar"

// True on GitHub Actions and other CI runners that set CI=true.
val isCi = System.getenv("CI") == "true"

// Runs git in the repository root. Returns trimmed stdout, or null when git fails.
fun runGit(vararg args: String): String? = try {
	val process = ProcessBuilder(listOf("git") + args.toList())
		.directory(rootProject.projectDir)
		.redirectErrorStream(true)
		.start()
	val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
	if (process.waitFor() == 0) out else null
} catch (e: Exception) {
	null
}

// versionCode is the number of commits on the checked-out history. It only grows, so every
// release is newer than the last. A shallow clone undercounts, so a release build refuses to start
// from one (see the taskGraph check below). Without git (a source archive), the build uses 1.
val isShallowClone = runGit("rev-parse", "--is-shallow-repository") == "true"
val appVersionCode: Int = runGit("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
// versionName is yyyyMMdd.<versionCode>; the VERSION_NAME environment variable overrides it.
val appVersionName: String = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
	?: "${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.$appVersionCode"

// Release signing comes only from environment variables. ~/.gradle/gradle.properties is not read,
// so a developer's local key cannot be used by CI by accident.
val releaseKeystore: File? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { File(it) }
val releaseStorePass: String? = System.getenv("KEYSTORE_PASS")?.takeIf { it.isNotEmpty() }
val releaseKeyAlias: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotEmpty() }
val releaseKeyPass: String? = System.getenv("KEY_PASS")?.takeIf { it.isNotEmpty() }
val releaseSigningReady: Boolean =
	releaseKeystore?.isFile == true &&
		releaseStorePass != null && releaseKeyAlias != null && releaseKeyPass != null

// Named individually so a failed release build says which variable is missing instead of
// reprinting the whole list.
val missingSigningInputs: List<String> = buildList {
	// A local copy, so the null check smart-casts without depending on how the script exposes
	// top-level properties.
	val keystore = releaseKeystore
	if (keystore == null) add("KEYSTORE_FILE")
	else if (!keystore.isFile) add("KEYSTORE_FILE (not an existing file: ${keystore.path})")
	if (releaseStorePass == null) add("KEYSTORE_PASS")
	if (releaseKeyAlias == null) add("KEY_ALIAS")
	if (releaseKeyPass == null) add("KEY_PASS")
}

android {
	namespace = "ws.xsoh.etar"
	testNamespace = "com.android.calendar.tests"
	compileSdk = 36

	defaultConfig {
		minSdk = 23
		targetSdk = 34
		versionCode = appVersionCode
		versionName = appVersionName
		applicationId = baseApplicationId
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		create("release") {
			if (releaseSigningReady) {
				storeFile = releaseKeystore
				storePassword = releaseStorePass
				keyAlias = releaseKeyAlias
				keyPassword = releaseKeyPass
			}
		}
	}

	buildTypes {
		release {
			// TODO: could be enabled for ProGuard minimization
			isMinifyEnabled = false
			isDebuggable = false
			// Release key when the environment provides it. Otherwise the debug key, for local builds
			// only: CI refuses to build release without the release key (see taskGraph check below).
			signingConfig = if (releaseSigningReady) {
				signingConfigs.getByName("release")
			} else {
				signingConfigs.getByName("debug")
			}
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".CalendarRecentSuggestionsProvider"
			)
			// shortcuts.xml cannot interpolate ${applicationId}; it reads this string instead.
			resValue("string", "shortcut_target_package", baseApplicationId)
		}

		debug {
			isMinifyEnabled = false

			applicationIdSuffix = ".debug"
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".debug.CalendarRecentSuggestionsProvider"
			)
			resValue("string", "shortcut_target_package", baseApplicationId + ".debug")
		}
	}

	buildFeatures {
        buildConfig = true
		viewBinding = true
	}


	lint {
		lintConfig = file("lint.xml")
		// In CI the release lint gate must be able to fail the build. lintVitalRelease is the only
		// check that sees release-only resource errors (ExtraTranslation, MissingDefaultResource),
		// and the CI hook at the bottom of this file runs it on every build. Locally the full
		// `lint` task still reports the known issues below, so developers keep the old behaviour.
		// TODO: Resolve lint errors due to 363aa9c237a33e9e1a40bdfd9039dcaaa855a5a0
		abortOnError = isCi
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

// A release build must never be signed with the debug key in CI. Locally it falls back to the
// debug key with a warning, so development builds keep working without the release secrets.
// Only tasks that produce or sign a release package count. Test tasks such as testReleaseUnitTest
// do not, so `./gradlew test` is not affected. Neither does packageReleaseResources: it only
// builds the compiled resource archive that AAPT and lint read, and lintVitalRelease pulls it in
// without ever producing an APK. A "package…Release" prefix test alone matched it and made a
// plain lint run demand the release key and the full git history.
val releasePackageTask =
	Regex("^(assemble|bundle|install|validateSigning)[A-Za-z]*Release$|^package[A-Za-z]*Release(Bundle)?$")

gradle.taskGraph.whenReady {
	val packagesRelease = allTasks.any { task ->
		task.project.path == ":app" && releasePackageTask.matches(task.name)
	}
	if (packagesRelease) {
		if (isCi && isShallowClone) {
			throw GradleException("Release build refused: versionCode needs full git history (fetch-depth: 0).")
		}
		if (!releaseSigningReady) {
			val missing = missingSigningInputs.joinToString(", ")
			if (isCi) {
				throw GradleException(
					"Release build refused before packaging: signing environment incomplete. " +
						"Missing: $missing. CI never falls back to the debug key; set the " +
						"KEYSTORE_BASE64, KEYSTORE_PASS, KEY_ALIAS and KEY_PASS secrets " +
						"(see docs/WORKFLOW.md)."
				)
			}
			logger.warn("WARNING: release signing environment incomplete ($missing). " +
				"Local release build is signed with the DEBUG key. Do not distribute it.")
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
		filter.includeTestsMatching("com.android.calendar.monthagenda.*")
		filter.includeTestsMatching("com.android.calendar.theme.*")
		filter.includeTestsMatching("com.android.calendar.settings.*")
		filter.includeTestsMatching("com.android.calendar.event.DropdownValuesTest")
		filter.includeTestsMatching("com.android.calendar.timezonepicker.TimeZoneSearchMatcherTest")
		filter.includeTestsMatching("com.android.calendar.timezonepicker.TimeZoneSearchRoutingTest")
		filter.includeTestsMatching("com.android.calendar.timezonepicker.TimeZoneSelectionRoutingTest")
		filter.includeTestsMatching("com.android.calendar.event.CalendarAccountGroupsTest")
		filter.includeTestsMatching("com.android.calendar.EventInfoRulesTest")
		filter.includeTestsMatching("com.android.calendar.EventInfoLayoutTest")
		filter.includeTestsMatching("com.android.calendar.DayTimeSlotPreviewTest")
		filter.includeTestsMatching("com.android.calendar.DayViewTimeSlotSourceTest")
		filter.includeTestsMatching("com.android.calendar.DayGridGeometryTest")
		filter.includeTestsMatching("com.android.calendar.DayTimeGeometryTest")
		filter.includeTestsMatching("com.android.calendar.agenda.AgendaItemStyleTest")
		filter.includeTestsMatching("com.android.calendar.agenda.AgendaItemLayoutTest")
		filter.includeTestsMatching("com.android.calendar.monthagenda.MonthAgendaLayoutProtectionTest")
		filter.includeTestsMatching("com.android.calendar.SlotTimeLabelsTest")
		filter.includeTestsMatching("com.android.calendar.NowIndicatorTest")
		filter.includeTestsMatching("com.android.calendar.DayWeekBackgroundMaskTest")
		filter.includeTestsMatching("com.android.calendar.EventInfoFullWindowTest")
		filter.includeTestsMatching("com.android.calendar.TimeLabelFormatTest")
		filter.includeTestsMatching("com.android.calendar.event.NewEventCalendarColorTest")
		filter.includeTestsMatching("com.android.calendar.agenda.AgendaCardWiringTest")
		// List every passing lunar test in the Actions log so the runs are
		// auditable there directly.
		testLogging.events(TestLogEvent.PASSED)
	}
	tasks.configureEach {
		if (name == "assembleDebug") {
			dependsOn("testDebugUnitTest")
			// The release workflow only runs on lineage-23.0, so without this a pull request never
			// sees lintVitalRelease and release-only lint errors surface after the merge. Running
			// it here makes them a CI failure on the branch instead. It needs no signing key:
			// lint analyzes sources and resources, it does not package or sign an APK.
			dependsOn("lintVitalRelease")
		}
	}
}

# Check the local Android build environment

Run before first integration, dependency changes, or a Gradle/JVM failure. A terminal build and an IDE sync can select different JDKs. Check both before describing a project as ready to open in Android Studio.

## Inspect the project and machine

Use the project's wrapper (`./gradlew`, or `gradlew.bat` on Windows), rather than a globally installed Gradle. Inspect only relevant properties; keep credentials and unrelated user settings out of output.

| Layer | Evidence to collect |
|---|---|
| Build versions | `gradle/wrapper/gradle-wrapper.properties` distribution; AGP and Kotlin plugin versions in build files, catalogs or convention plugins |
| Terminal JVM | `java -version`, `JAVA_HOME` when set, and `./gradlew --version`; record the actual launcher and daemon JVMs when reported |
| Daemon overrides | `gradle/gradle-daemon-jvm.properties`; relevant `org.gradle.java.home` entries in project/user Gradle properties and invocation options |
| IDE Gradle JVM | `.idea/gradle.xml` `gradleJvm`; resolve its macro, registered JDK name or path, then run that JDK's `bin/java -version` |
| Local IDE macro | `GRADLE_LOCAL_JAVA_HOME` resolves `java.home` in `.gradle/config.properties`; `JAVA_HOME` resolves the IDE's environment, which may differ from a shell |
| Android tools | SDK location, installed platform/build tools, minSdk/compileSdk, Java/Kotlin bytecode targets and any compilation toolchain |

Read the named hidden/ignored files directly. A normal `rg --files` search often skips `.idea/` and `.gradle/`. Read only JDK-related keys from user Gradle properties, which can also contain repository credentials.

If daemon JVM criteria exist, account for them before choosing a replacement JDK: they take precedence over `JAVA_HOME` and `org.gradle.java.home`. The JDK running Android Studio itself, the JDK running Gradle, and the compilation language/bytecode targets are separate settings. SDK 1.1.0's Java/JVM target 11 does not select the Gradle runtime.

If IDE configuration is absent, inspect the intended IDE's default/bundled JDK when available. Android Studio's local macro defaults to its bundled JBR, so a newer Studio can select a JDK that an older wrapper cannot run. Record an unknown IDE selection as unverified; a successful terminal build alone leaves first import unchecked.

## Choose a compatible local JDK

Check the wrapper's supported **runtime** Java versions and the selected AGP's minimum JDK/Gradle requirements against the [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html#java_runtime) and [Android build JDK guidance](https://developer.android.com/build/jdks). Follow the selected plugin's release notes when its requirements differ.

For example, Gradle **8.13** runs on Java **8–23**, while AGP **8.x** requires Java **17+**. An installed JDK **21** fits that combination. Java **25** needs Gradle **9.1.0+** to run Gradle; selecting Studio's bundled JDK 25 for an 8.13 project causes an import failure even if terminal builds use JDK 21.

Prefer an already installed JDK compatible with the existing wrapper and plugins. On macOS, `/usr/libexec/java_home -V` lists registered installations; also check a configured IDE/JDK path when it is not listed there. Verify the candidate's actual version and path. Preserve working wrapper, AGP, Kotlin and bytecode target choices; a Gradle runtime mismatch is usually resolved by selecting a compatible JDK rather than changing the SDK dependency or upgrading the whole build.

Apply a scoped configuration change:

- For Android Studio, use **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** for the current project. Keep the IDE's own runtime unchanged.
- If the project uses `GRADLE_LOCAL_JAVA_HOME`, update only `java.home` in its local `.gradle/config.properties`, preserving other entries. This can also prepare the local JDK before first import. If the project has an explicit named JDK or daemon JVM criteria, resolve that selection instead of assuming the local macro controls it.
- For terminal builds, select the compatible JDK for the current command/session and verify the effective daemon JVM. Avoid changing global Java/Gradle settings for other projects.
- Keep machine-specific absolute JDK paths in ignored local configuration. Share the required JDK version and setup instructions, not the maintainer's machine path. If no compatible JDK is installed, identify the required range and follow the user's installation policy.

## Verify the selected runtime and IDE import

1. Run the wrapper's `--version`, then `help` or the requested app build under the selected JDK. `--version` alone does not evaluate the build scripts and plugins.
2. Run the actual module/variant compile or assemble task and dependency inspection described in [project wiring](project-wiring.md#build-and-handoff).
3. If Android Studio is available, sync the project and observe completion. An IDE error or ongoing sync remains unresolved even when the terminal build passed. If IDE access is unavailable, state the inspected selection and leave sync unverified with the exact setting to check.
4. Recheck the local JDK after moving the project or opening it in a different IDE installation. Existing terminal build artifacts do not verify the new IDE's runtime selection.

Handoff: wrapper and AGP versions; terminal Gradle JVM; resolved IDE Gradle JVM or unknown; scoped configuration change; exact build outcome; observed IDE sync outcome or unverified.

Official references: [Android Studio JDK selection and local macros](https://developer.android.com/build/jdks#gradle-jdk), [Gradle Java runtime compatibility](https://docs.gradle.org/current/userguide/compatibility.html#java_runtime), [daemon JVM criteria](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria).

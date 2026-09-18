plugins {
    id("java")
    id("idea")
    id("net.fabricmc.fabric-loom") version "1.17.21"
}

// ---------------------------------------------------------------------------
// Versions
//
// All of these were verified against maven.fabricmc.net and the build scripts
// of VoxelMap / VoxelConfig for Minecraft 26.3. Minecraft 26.3 ships with
// official Mojang names, and no Yarn artifact exists for it, so Loom needs no
// `mappings` declaration at all.
// ---------------------------------------------------------------------------
val minecraftVersion = "26.3"
val fabricLoaderVersion = "0.19.5"
val fabricApiVersion = "0.160.7+26.3"
val voxelConfigVersion = "1.0.2"
val modMenuVersion = "21.0.0-beta.1"
val junitVersion = "5.14.4"
val modVersion = "0.1.0"

group = "de.tobi.voxelprint"
version = "$minecraftVersion-$modVersion"

base {
    archivesName = "voxelprint"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// ---------------------------------------------------------------------------
// Shading
//
// VoxelConfig is a plain library, not a mod, so it is not present at runtime
// unless we ship it ourselves. VoxelMap solves this by shading VoxelConfig into
// its own jar; VoxelPrint follows the same strategy with plain Gradle wiring
// instead of the Shadow plugin, because a single-module build does not need the
// extra plugin to copy one dependency into the jar.
// ---------------------------------------------------------------------------
val shade = configurations.dependencyScope("shade")
val shadeClasspath = configurations.resolvable("shadeClasspath") {
    extendsFrom(shade.get())
}
configurations.named("implementation") {
    extendsFrom(shade.get())
}

repositories {
    mavenCentral()
    maven {
        name = "iani"
        url = uri("https://www.iani.de/nexus/content/repositories/releases/")
        content { includeGroup("de.voxelmap") }
    }
    maven {
        name = "modrinth"
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    "shade"("de.voxelmap:voxelconfig:$voxelConfigVersion")
    implementation("maven.modrinth:cubesideutilsfabricclient:1.1.2")
    include("maven.modrinth:cubesideutilsfabricclient:1.1.2")

    // Optional integration: compiled against, never required at runtime.
    compileOnly("maven.modrinth:modmenu:$modMenuVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        // Name deprecated Minecraft APIs instead of only hinting that one was
        // used -- across game versions those are the things that break first.
        options.compilerArgs.add("-Xlint:deprecation")
    }

    processResources {
        // Resolved at configuration time and captured by the action below.
        // Reading project.version from inside filesMatching would touch the
        // Project at execution time, which Gradle's configuration cache forbids.
        val modVersion = project.version.toString()
        inputs.property("version", modVersion)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to modVersion))
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(provider { shadeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } }) {
            exclude(
                "META-INF/MANIFEST.MF",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/maven/**",
                "LICENSE.md"
            )
        }

        from(rootDir.resolve("LICENSE"))
    }
}

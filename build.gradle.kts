plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("com.github.ben-manes.versions")
    id("org.owasp.dependencycheck")
    id("maven-publish")
    id("signing")
}

group = "com.wilsonfranca"

repositories {
    mavenLocal()
    mavenCentral()
}
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}
dependencies {
    api("com.wilsonfranca:dynamodb-client-test-autoconfigure:1.0.1-SNAPSHOT")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

tasks.register<Jar>("javadocJar") {
    dependsOn("javadoc")
    archiveClassifier.set("javadoc")
    from(tasks["javadoc"].outputs)
}

tasks.register("releaseSnapshot") {
    dependsOn("build")
    doLast {
        releaseVersion()
    }
}

tasks.register("createPatchSnapshot") {
    dependsOn("build")
    doLast {
        newPatchSnapshotVersion()
    }
}

val zipArtifacts by tasks.registering(Zip::class) {
    dependsOn("publishMavenJavaPublicationToInternalRepoRepository")
    from("${layout.buildDirectory.get()}/repo") {
        exclude("**/maven-metadata*.*")
    }
    archiveFileName.set("${project.name}-${version}.zip")
    destinationDirectory.set(file("${layout.buildDirectory.get()}/outputs"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name = "DynamoDB Client Test Starter"
                description = "DynamoDB Client Test Spring Boot Starter"
                url = "https://github.com/wilsonrf/dynamodb-client-starter"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "wilsonrf"
                        name = "Wilson da Rocha França"
                        email = "wilsonrf@gmail.com"
                    }
                }
                scm {
                    connection = "scm:git:git@github.com:wilsonrf/dynamodb-client-test-starter.git"
                    developerConnection = "scm:git:git@github.com:wilsonrf/dynamodb-client-test-starter.git"
                    url = "https://github.com/wilsonrf/dynamodb-client-test-starter"
                }
            }
        }

        repositories {
            maven {
                name = "internalRepo"
                url = uri("${layout.buildDirectory.get()}/repo")
            }

            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/wilsonrf/dynamodb-client-test-starter")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

signing {
    setRequired {
        gradle.taskGraph.allTasks.any { it is PublishToMavenLocal }.not()
    }

    val signingKey: String? by project
    val signingKeyId: String? by project
    val signingPassword: String? by project

    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)

    sign(publishing.publications["mavenJava"])
}

/**
 * This function returns the current version of the project.
 */
fun currentVersion(): String {
    return version.toString()
}

/**
 * This function returns the current major version of the project.
 */
fun currentMajor(): Int {
    return currentVersion().split(".")[0].toInt()
}

/**
 * This function returns the current minor version of the project.
 */
fun currentMinor(): Int {
    return version.toString().split(".")[1].toInt()
}

/**
 * This function returns the current patch version of the project.
 */
fun currentPatch(): Int {
    return version.toString().split(".")[2].replace("-SNAPSHOT", "").toInt()
}

/**
 * This function returns the files that need to be updated with the new version.
 */
fun filesToBeUpdated(): List<String> {
    return listOf("gradle.properties", "README.adoc", "build.gradle.kts")
}

/**
 * This function updates the version in the files that need to be updated.
 */
fun updateVersionInFiles(version: String) {
    filesToBeUpdated().forEach {
        val file = File(it)
        val content = file.readText()
        val currentVersion = currentVersion()
        println("Updating version from $currentVersion to $version in file $it")
        val updatedContent = content.replace(currentVersion, version)
        file.writeText(updatedContent)
    }
}

/**
 * This function checks if this is the current main branch.
 */
fun isMainBranch(): Boolean {
    val gitCommand = "git rev-parse --abbrev-ref HEAD"
    val process = ProcessBuilder(gitCommand.split(" ")).start()
    process.waitFor(10, TimeUnit.SECONDS)
    val exitCode = process.exitValue()
    if (exitCode != 0) {
        throw RuntimeException("Failed get current branch")
    }
    val currentBranch = process.inputStream.bufferedReader().readText().trim()
    println("Current branch is $currentBranch")
    return currentBranch == "main"
}

/**
 * This function create a release version of the snapshot version, commit and create a tag.
 */
fun releaseVersion() {
    val currentVersion = currentVersion()
    if (!isSnapshotVersion() || !isMainBranch()) {
        throw RuntimeException("This task can only be executed in the main branch and in a snapshot version")
    }
    val version = currentVersion.replace("-SNAPSHOT", "")
    updateVersionInFiles(version)
    gitAdd(filesToBeUpdated())
    gitCommit("Release version $version")
    gitTag(version)
}

/**
 * This function add files to git.
 */
fun gitAdd(files: List<String>) {
    val command = "git add ${files.joinToString(" ")}"
    val process = ProcessBuilder(command.split(" ")).start()
    process.waitFor(10, TimeUnit.SECONDS)
    val exitCode = process.exitValue()
    if (exitCode != 0) {
        throw RuntimeException("Failed to add files to git")
    }
}

/**
 * This function commit the changes in git.
 */
fun gitCommit(message: String) {
    exec {
        commandLine = listOf("git", "commit", "-m", message)
    }
}

/**
 * This function create a tag in git.
 */
fun gitTag(version: String) {
    exec {
        commandLine = listOf("git", "tag", "v$version")
    }
}

/**
 * This function checks if the current version is a snapshot version.

 */
fun isSnapshotVersion(): Boolean {
    return currentVersion().contains("SNAPSHOT")
}

/**
 * This function creates a new patch snapshot version.
 */
fun newPatchSnapshotVersion() {
    val currentVersion = currentVersion()
    if (isSnapshotVersion() || !isMainBranch()) {
        throw RuntimeException("This task can only be executed in the main branch and in a snapshot version")
    }
    val version = currentVersion.split(".")
    val newVersion = "${version[0]}.${version[1]}.${version[2].toInt() + 1}-SNAPSHOT"
    updateVersionInFiles(newVersion)
    gitAdd(filesToBeUpdated())
    gitCommit("new snapshot $newVersion")
}

private fun isRunningInCI(): Boolean = System.getenv("CI") == "true"
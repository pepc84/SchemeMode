plugins { java }
group   = "processing.mode.scheme"
version = "0.1.0"

val p4 = System.getenv("PROCESSING4_DIR")
    ?: "${System.getProperty("user.home")}/Projects/processing4"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

dependencies {
    // processing.app.* lives in java/build/libs/java*.jar
    compileOnly(fileTree("$p4/java/build/libs") { include("java*.jar") })
    // app.jar has Base, Editor, Mode etc.
    compileOnly(fileTree("$p4/app/build/libs") { include("app*.jar") })
    // core
    compileOnly(fileTree("$p4/core/library") { include("*.jar") })
}

sourceSets { main { java { srcDirs("src/java") } } }
tasks.jar {
    archiveBaseName = "SchemeMode"
    archiveVersion = ""
}

val modeDir = "${System.getProperty("user.home")}/sketchbook/modes/SchemeMode"

tasks.register("install") {
    dependsOn("jar")
    group = "install"
    doLast {
        copy { from(tasks.jar.get().archiveFile); into("$modeDir/mode") }
        copy { from("resources"); into("$modeDir/resources") }
        copy { from("mode.properties"); into(modeDir) }
        println("Installed to $modeDir")
    }
}

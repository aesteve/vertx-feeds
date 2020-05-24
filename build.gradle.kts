val vertxVersion = "4.0.0-milestone4"
val mainVerticle = "io.vertx.examples.feeds.verticles.MainVerticle"

buildscript {
	// Gradle plugins
	dependencies {
		classpath("com.github.jengelman.gradle.plugins:shadow:5.2.0") // FatJar packaging
	}
}

plugins {
	java
	application
	id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
	mavenCentral()
}

group = "com.github.aesteve"
version = ""

tasks.withType<JavaCompile> {
	sourceCompatibility = JavaVersion.VERSION_14.toString()
    targetCompatibility = JavaVersion.VERSION_14.toString()
}


dependencies {
	
	// Vert.x standard
	implementation("io.vertx:vertx-core:$vertxVersion")
	implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
	
	// MongoDB
	implementation("io.vertx:vertx-mongo-client:$vertxVersion")
	implementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:2.2.0")
	// Redis
	implementation("io.vertx:vertx-redis-client:$vertxVersion")
	implementation("it.ozimov:embedded-redis:0.7.2")


	// Handlebars
	implementation("io.vertx:vertx-web-templ-handlebars:$vertxVersion")
	implementation("com.github.jknack:handlebars:2.1.0")
	
	// RSS
	implementation("com.rometools:rome:1.12.2")


    runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
}

application {
	mainClassName = "io.vertx.core.Launcher"
}


tasks {
    shadowJar {
        archiveClassifier.set("")
        manifest {
            attributes["Main-Verticle"] = "io.vertx.examples.feeds.verticles.MainVerticle"
        }
        mergeServiceFiles {
            include("META-INF/services/io.vertx.core.spi.VerticleFactory")
        }
        into("webroot") {
            from("webroot")
        }
        into("templates") {
            from("templates")
        }
    }
    withType<JavaExec> {
        args = listOf("run", mainVerticle)
    }
    withType<Wrapper> {
        gradleVersion = "6.4"
    }
}

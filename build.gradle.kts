import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val vertxVersion = "3.6.2"
val mainVerticle = "io.vertx.examples.feeds.verticles.MainVerticle"

buildscript {
	// Gradle plugins
	dependencies {
		classpath("com.github.jengelman.gradle.plugins:shadow:4.0.2") // FatJar packaging
	}
}

plugins {
	java
	application
	id("com.github.johnrengelman.shadow") version "4.0.2"
}

repositories {
	mavenCentral()
}

group = "com.github.aesteve"
version = ""

tasks.withType<JavaCompile> {
	sourceCompatibility = "1.8"
}


dependencies {
	
	// Vert.x standard
	compile("io.vertx:vertx-core:$vertxVersion")
	compile("io.vertx:vertx-web:$vertxVersion")
	
	// MongoDB
	compile("io.vertx:vertx-mongo-client:$vertxVersion")
	compile("de.flapdoodle.embed:de.flapdoodle.embed.mongo:2.2.0")
	// Redis
	compile("io.vertx:vertx-redis-client:$vertxVersion")
	compile("it.ozimov:embedded-redis:0.7.2")


	// Handlebars
	compile("io.vertx:vertx-web-templ-handlebars:$vertxVersion")
	compile("com.github.jknack:handlebars:2.1.0")
	
	// RSS
	compile("com.rometools:rome:1.5.0")
}

application {
	mainClassName = "io.vertx.core.Launcher"

}


tasks.shadowJar {
	classifier = ""
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

tasks.withType<JavaExec> {
	args = listOf("run", mainVerticle)
}

tasks.withType<Wrapper> {
	gradleVersion = "5.1.1"
}

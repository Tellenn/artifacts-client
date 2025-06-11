package com.tellenn.artifacts

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@Suppress("unused")
@SpringBootApplication
class TellennArtifactsClientApplication


object AppConfig {
	var maxLevel: Int = 0// Default value, can be of any type you need
}

fun main(args: Array<String>) {
	runApplication<TellennArtifactsClientApplication>(*args)
}

package com.tellenn.artifacts

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TellennArtifactsClientApplication

fun main(args: Array<String>) {
	runApplication<TellennArtifactsClientApplication>(*args)
}

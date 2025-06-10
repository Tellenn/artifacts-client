package com.tellenn.artifacts.clients

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tellenn.artifacts.clients.requests.ArtifactsRequestBody
import lombok.extern.slf4j.Slf4j
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.springframework.beans.factory.annotation.Value

@Slf4j
abstract class BaseArtifactsClient() {

    @Value("\${artifacts.api.url}")
    private lateinit var url: String

    @Value("\${artifacts.api.key}")
    private lateinit var key: String

    val client = OkHttpClient()

    val objectMapper = jacksonObjectMapper()

    fun sendGetRequest(path : String) : Response {
        val getRequest = Request.Builder()
            .url(url+path)
            .build()

        client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Réponse non réussie: ${response.code}")
            println("Réponse GET: ${response.body?.string()}")
            // TODO : Generic Response object from artifacts
            return response
        }
    }

    fun sendPostRequest(path : String, body: String) : Response {
        val postBody = body.trimIndent()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = postBody.toRequestBody(mediaType)

        val postRequest = Request.Builder()
            .url(url+path)
            .post(requestBody)
            .header("Authorization", "Bearer $key")
            .build()

        client.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Réponse non réussie: ${response.code}")
            println("Réponse POST: ${response.body?.string()}")
            // TODO : Generic Response object from artifacts
            return response
        }
    }
}
package com.tellenn.tellenn_artifacts_client.clients

import lombok.extern.slf4j.Slf4j

@Slf4j
@Component
class BaseArtifactsClient {


    @Value("\${artifacts.api.url}")
    private lateinit var url: String

    @Value("\${artifacts.api.key}")
    private lateinit var key: String


    fun sendRequest(ArtifactsRequestBody body){
        val client = OkHttpClient()

        // Exemple de requête GET
        val getRequest = Request.Builder()
            .url("https://jsonplaceholder.typicode.com/posts/1")
            .build()

        client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Réponse non réussie: ${response.code}")
            println("Réponse GET: ${response.body?.string()}")
        }

        // Exemple de requête POST
        val postBody = body.toString().trim

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = postBody.toRequestBody(mediaType)

        val postRequest = Request.Builder()
            .url("https://jsonplaceholder.typicode.com/posts")
            .post(requestBody)
            .build()

        client.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Réponse non réussie: ${response.code}")
            println("Réponse POST: ${response.body?.string()}")
        }
    }
}
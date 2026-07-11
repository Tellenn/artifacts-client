package com.tellenn.artifacts.clients

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BaseArtifactsClientHttpConfigTest {

    @Test
    fun `le client HTTP ne rejoue jamais silencieusement une requête sur coupure de connexion`() {
        // À minuit UTC l'API casse les connexions keep-alive poolées : OkHttp rejouait alors
        // silencieusement des POST non idempotents déjà traités côté serveur (492/490/478 en cascade).
        assertFalse(buildArtifactsHttpClient().retryOnConnectionFailure)
    }
}

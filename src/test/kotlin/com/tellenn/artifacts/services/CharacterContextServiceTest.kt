package com.tellenn.artifacts.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CharacterContextServiceTest {

    private lateinit var service: CharacterContextService

    @BeforeEach
    fun setUp() {
        service = CharacterContextService()
    }

    @Test
    fun `renvoie l'objectif seul quand aucune etape n'est en cours`() {
        // given
        service.setObjective("Renoir", "Craft de iron_sword pour la banque")

        // when
        val objectives = service.getAllObjectives()

        // then
        assertThat(objectives["Renoir"]).isEqualTo("Craft de iron_sword pour la banque")
    }

    @Test
    fun `concatene l'objectif et l'etape courante`() {
        // given
        service.setObjective("Renoir", "Craft de iron_sword pour la banque")
        service.setStep("Renoir", "collecte de 4× ash_wood")

        // when
        val objectives = service.getAllObjectives()

        // then
        assertThat(objectives["Renoir"])
            .isEqualTo("Craft de iron_sword pour la banque — collecte de 4× ash_wood")
    }

    @Test
    fun `setObjective efface l'etape du personnage`() {
        // given
        service.setObjective("Renoir", "Craft de iron_sword pour la banque")
        service.setStep("Renoir", "collecte de 4× ash_wood")

        // when
        service.setObjective("Renoir", "Nettoyage de la banque")

        // then
        assertThat(service.getAllObjectives()["Renoir"]).isEqualTo("Nettoyage de la banque")
    }

    @Test
    fun `clearStep retire l'etape sans toucher l'objectif`() {
        // given
        service.setObjective("Renoir", "Craft de iron_sword pour la banque")
        service.setStep("Renoir", "assemblage de 1× iron_sword")

        // when
        service.clearStep("Renoir")

        // then
        assertThat(service.getAllObjectives()["Renoir"]).isEqualTo("Craft de iron_sword pour la banque")
    }

    @Test
    fun `une etape sans objectif n'apparait pas dans la liste`() {
        // given — GatheringService peut publier avant que le job ait posé un objectif
        service.setStep("Renoir", "collecte de 4× ash_wood")

        // when / then
        assertThat(service.getAllObjectives()).isEmpty()
    }

    @Test
    fun `l'etape d'un personnage n'affecte pas les autres`() {
        // given
        service.setObjective("Renoir", "Craft pour la banque")
        service.setObjective("Kepo", "Minage")
        service.setStep("Renoir", "assemblage de 1× iron_sword")

        // when / then
        assertThat(service.getAllObjectives()["Kepo"]).isEqualTo("Minage")
    }
}

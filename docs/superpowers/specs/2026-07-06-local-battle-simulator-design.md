# Design — Simulateur de combat local (Battle Simulator)

**Date :** 2026-07-06
**Statut :** Validé (design), en attente de plan d'implémentation
**Package :** `com.tellenn.artifacts.services.battlesim`

## Objectif

Reconstruire un simulateur de combat **local** (sans appel réseau) fidèle aux mécaniques
de l'API Artifacts MMO, afin de :

- ne plus dépendre du rate-limit de `/simulation/fight` (~1 req/s) pour les décisions de combat ;
- modéliser correctement le poison et l'antipoison (cause de la suppression de l'ancien simulateur) ;
- permettre des **séries de N simulations** (défaut 10, configurable) comparables aux résultats
  de l'API officielle.

L'ancien `simulate()` était un modèle **statistique moyenné** (dégâts moyens par tour) : il ne
jouait pas les tours réellement et ne modélisait ni le poison, ni les seuils conditionnels, ni la
variance liée aux coups critiques. Le nouveau moteur est **tour-par-tour + Monte Carlo**.

## Décisions cadrées (brainstorming)

| Sujet | Décision |
|-------|----------|
| Approche | Tour-par-tour fidèle + RNG des crits, répété N fois (Monte Carlo) |
| Intégration | **Standalone** : nouvelle méthode `simulateLocally`, sans toucher aux appelants de `simulateWithApi`. Bascule des appelants dans un second temps. |
| Couverture des effets | **Catalogue complet** de l'API ; effet inconnu → WARN + ignoré |
| Trigger potion `heal` | Consommée au début du tour si HP `< 50 %` (seuil paramétrable) |
| Ciblage monstre en 3v1 | 90 % → perso avec le plus de `threat` ; 10 % → perso avec le moins de HP |
| Multi-perso | 1 **ou** 3 personnages vs 1 monstre (effets alliés inclus) |

## Principe clé : stats agrégées vs effets de combat

`ArtifactsCharacter` (renvoyé par l'API) porte déjà ses **stats agrégées, équipement inclus** :
`attack_*`, `dmg_*`, `res_*`, `max_hp`, `critical_strike`, `initiative`, `threat`.
→ Le moteur **ne recalcule jamais** les stats depuis les pièces d'équipement.

En revanche, les **effets de combat comportementaux** (poison, lifesteal, burn, healing, restore, …)
ne sont pas des stats : ils doivent être **collectés** depuis :

- chaque item équipé (`weapon_slot`, `rune_slot`, armures, anneaux, amulette, artefacts) via `ItemRepository.findByCode` ;
- les deux slots utilitaires (potions) `utility1_slot` / `utility2_slot` ;
- les effets propres du **monstre** (`MonsterData.effects`).

## Sources de données (100 % local)

| Donnée | Source |
|--------|--------|
| Monstre | `MonsterRepository.findByCode(code)` |
| Effets des items / potions | `ItemRepository.findByCode(code)` → `ItemDetails.effects` |
| Stats du personnage | objet `ArtifactsCharacter` fourni par l'appelant |

**Important — la sémantique des effets est dans le code, pas en DB.** Sur un `Effect` porté par
un item ou un monstre, on ne dispose que de `code` + `value` (+ `description` parfois nulle). Le
moteur n'a **pas** besoin de la description : chaque effet a un `EffectHandler` qui encode son
comportement ; le `value` fournit la magnitude, le `code` route vers le handler.

## Sync des effets en DB (référence + garde-fou de couverture)

On ajoute une **sync des effets**, calquée sur `MonsterSyncService` + `PaginatedSyncUtils.syncAll` :

- `EffectClient` — extends `BaseArtifactsClient`, `GET /effects`.
- `EffectDocument` — `@Document("effects")` : `code`, `name`, `type`, `subtype`, `description`.
- `EffectRepository` — `MongoRepository<EffectDocument, String>`.
- `EffectSyncService` — miroir de `MonsterSyncService`.

**Rôle : référence/runtime uniquement, PAS la logique du moteur.** Son intérêt principal est de
servir de **garde-fou de couverture** : s'assurer qu'aucun effet de type `combat`/`consumable` ne
reste sans handler.

**Garde-fou = test qui échoue le build (offline, sans DB).** Pour éviter le problème connu
Testcontainers ✗ Docker Desktop 29.x, le test **ne lit pas la DB** : il compare un **fixture JSON
committé** (`src/test/resources/battlesim/effects-snapshot.json`, snapshot de `/effects` filtré
`combat` + `consumable`) aux clés du `EffectRegistry`. Un effet du fixture sans handler → **échec
du build**. Le fixture est régénérable à partir de `EffectSyncService` quand l'API évolue ; un
nouvel effet non implémenté casse alors le build à la mise à jour du fixture.

En complément, à l'exécution, un effet inconnu rencontré sur un item/monstre est **ignoré + WARN**
(filet de sécurité, ne bloque jamais un combat).

## Architecture

```
services/battlesim/
  BattleSimulatorService.kt        façade existante ; on AJOUTE simulateLocally(...)
  engine/
    Combatant.kt                   état mutable d'un combattant pendant le combat
    FightEngine.kt                 boucle tour-par-tour d'UN combat → FightOutcome
    MonteCarloRunner.kt            relance FightEngine N fois (RNG seedable) → agrégat
    DamageCalculator.kt            formules attaque/résistance/crit + arrondi ".5 vers le haut"
  effects/
    EffectHandler.kt               interface (hooks par timing)
    EffectRegistry.kt              code → handler ; inconnu = WARN + ignore
    handlers/                      un fichier par effet (PoisonHandler, BurnHandler, …)
  loadout/
    CombatEffectResolver.kt        collecte les effets de combat des items équipés + potions
  model/
    FightOutcome.kt
    CombatLog.kt
    LocalSimulationResult.kt

services/sync/
  EffectSyncService.kt             sync /effects → DB (miroir de MonsterSyncService)
clients/
  EffectClient.kt                  GET /effects
db/documents/
  EffectDocument.kt                @Document("effects")
db/repositories/
  EffectRepository.kt              MongoRepository<EffectDocument, String>
```

**Isolation / testabilité :**
- `DamageCalculator` : fonctions pures, aucune dépendance → testable seul.
- `EffectHandler` : un handler = un effet, garde conditionnelle interne → testable seul.
- `FightEngine` : orchestration d'un combat, RNG injecté → déterministe sous seed.
- `MonteCarloRunner` : agrégation statistique de N `FightOutcome`.

**RNG injecté** (`kotlin.random.Random`, seedable) → tests reproductibles.

## Le `Combatant`

État mutable porté pendant le combat, **pour Character comme pour Monster** (symétrie : un
effet peut appartenir à l'un ou l'autre camp) :

- `hp` / `maxHp` courants (le ratio HP est recalculé à chaque hook, jamais figé) ;
- stats effectives par élément (attack/dmg/res), `criticalStrike`, `initiative`, `threat` ;
- `turnsPlayed` : compteur de tours joués par ce combattant (pour les effets périodiques) ;
- effets de combat actifs (issus de `CombatEffectResolver` ou du monstre) ;
- potions restantes (`utility1SlotQuantity`, `utility2SlotQuantity`) ;
- état conditionnel : pile de `poison`, `barrier` HP restante, paliers `greed` franchis,
  flags « once/N per combat » (`shellUsed`, `berserkUsed`, `guardActivations`, `lastMirrorTurn`, …).

## Déroulé d'un combat (`FightEngine`)

Aligné sur la doc officielle (max **100 tours**, sinon défaite du/des personnage(s)).

1. **Setup (pré-combat)** — effets « start of fight » : `boost_hp`, `boost_dmg_*`, `boost_res_*`,
   `barrier` initiale. Ordre de jeu par **initiative** ; égalité → HP le plus haut → RNG.
2. **Boucle de tours (≤ 100)**. À chaque tour d'un combattant, dans cet ordre :
   - **Start-of-turn** (ordre canonique documenté, voir plus bas) : dégâts périodiques (poison,
     burn) → antipoison → soins de seuil (`restore`, `splash_restore`) → soins périodiques
     (`healing`, `reconstitution`, `void_drain`, `healing_aura`).
   - **Décision potion** : si HP `< 50 %` et potion `heal` disponible → consommer (décrémente la
     quantité de la potion correspondante).
   - **Attaque** : `DamageCalculator` calcule par élément
     `Round(attack × (1 + dmg%/100)) × (1 − res/100)`, tirage crit (×1.5) via RNG, puis modificateurs
     défenseur (`sun_shield`, `shell`, `barrier`, `enchanted_mirror`, `corrupted`) et attaquant
     (`lifesteal`, `greed`, `frenzy`, `berserker_rage`).
   - **Fin de combat** si un camp atteint 0 HP.
3. **Résultat** : `FightOutcome(winner, turns, finalHpParCombattant, logs)`.

**Ciblage du monstre en multi-perso (3v1) :** à chaque attaque, RNG :
90 % → personnage avec le plus de `threat` ; 10 % → personnage avec le moins de HP.

## Effets = handlers isolés

```kotlin
interface EffectHandler {
    val code: String
    fun onFightStart(ctx: FightContext, owner: Combatant, value: Int) {}
    fun onTurnStart(ctx: FightContext, owner: Combatant, value: Int) {}
    fun onDealDamage(ctx: FightContext, attacker: Combatant, defender: Combatant,
                     dmg: DamageBreakdown, value: Int): DamageBreakdown = dmg
    fun onTakeDamage(ctx: FightContext, defender: Combatant, attacker: Combatant,
                     dmg: DamageBreakdown, value: Int): DamageBreakdown = dmg
    fun onCritical(ctx: FightContext, attacker: Combatant, defender: Combatant, value: Int) {}
}
```

`EffectRegistry` mappe `code → handler`. **Chaque handler évalue lui-même sa garde conditionnelle**
contre l'état courant du `Combatant` (responsabilité unique, Open/Closed : ajouter un effet =
ajouter un handler sans toucher au moteur). Effet inconnu → `logger.warn(...)` + ignoré.

### Effets conditionnels — gardes évaluées dans le handler

| Effet | Hook | Garde |
|-------|------|-------|
| `restore` | onTurnStart | `owner.hp < maxHp * 0.5` |
| `splash_restore` | onTurnStart | un allié `< 50 %` HP (cible = celui ayant perdu le plus) |
| `shell` | onTakeDamage | `hp < 40 % && !shellUsed` (once ; boss/raid seulement) |
| `berserker_rage` | onTakeDamage | `hp < 25 % && !berserkUsed` (once) |
| `greed` | onTakeDamage | à chaque palier de `−10 % max HP` franchi |
| `healing` / `reconstitution` / `void_drain` / `healing_aura` | onTurnStart | `turnsPlayed % période == 0` |
| `enchanted_mirror` | onTakeDamage | `ctx.turn − lastMirrorTurn >= 3` |
| `guard` | onTakeDamage (allié) | allié `< 50 %` HP, **max 3×/combat** |
| `sun_shield` | onTakeDamage | premier coup subi du tour |
| `poison` | onFightStart / onTurnStart | applique/tick la pile de poison |
| `antipoison` | onTurnStart | réduit la pile de poison si présente |
| `burn` | onTurnStart | dégât décroissant −10 % par tour |
| `corrupted` | onTakeDamage | réduit la res de l'élément subi (peut passer négatif) |
| `barrier` | onFightStart + tous les 5 tours | redirige les coups tant que HP barrière > 0 |
| `berserker_rage` / `frenzy` / `christmas_magic` | onCritical / onDealDamage | buff dégâts |

**Ordre d'évaluation canonique par hook** (documenté et testé, car c'est la principale source de
divergence) : dégâts périodiques (poison, burn) → antipoison → soins de seuil → soins périodiques.
Exemple : le poison de start-of-turn réduit les HP **avant** que `restore` teste le seuil 50 %.

### Catalogue d'effets à implémenter (source API `/effects`)

- **Buffs :** `boost_hp`, `boost_dmg_{fire,water,air,earth}`, `boost_res_{fire,water,air,earth}`,
  `sun_shield`, `greed`, `vampiric_strike`.
- **Soins :** `restore`, `splash_restore`, `healing`, `reconstitution`, `void_drain`, `healing_aura`.
- **Spéciaux :** `poison`, `lifesteal`, `burn`, `corrupted`, `guard`, `shell`, `frenzy`,
  `berserker_rage`, `barrier`, `christmas_magic`, `protective_bubble`, `enchanted_mirror`.
- **Autres :** `antipoison`.
- **Consommables (potions) :** `heal` (actif en combat, trigger seuil), `boost_*` (start of fight).
- **Équipement :** effets `stat` → déjà agrégés dans `ArtifactsCharacter`, **non rejoués**.

## Sortie

```kotlin
data class LocalSimulationResult(
    val wins: Int,
    val losses: Int,
    val winrate: Int,               // arrondi comme l'API
    val avgTurns: Double,
    val results: List<FightOutcome> // 1 par run : winner, turns, finalHp/combattant, logs
)
```

Façade (ajout, sans toucher l'API existante) :

```kotlin
fun simulateLocally(
    monsterCode: String,
    characters: List<ArtifactsCharacter>,
    runs: Int = 10,
    seed: Long? = null,
): LocalSimulationResult
```

Surcharge mono-personnage pour l'ergonomie : `simulateLocally(monsterCode, character, runs, seed)`.

## Stratégie de test

1. **Combats déterministes = match exact.** Fixtures où le crit est impossible
   (`critical_strike = 0`) → aucun RNG → assertions sur **vainqueur, nombre de tours et HP final**
   identiques à l'exemple d'API. Prouve les formules et l'ordre des effets.
2. **Combats avec crit = winrate sous tolérance.** Beaucoup de runs (~1000) avec seed fixe →
   `winrate` local dans une marge `±X %` du winrate API (marge fixée d'après la variance des exemples).
3. **Tests unitaires par handler** — chaque effet isolé sur un `Combatant` monté à la main,
   gardes conditionnelles incluses (seuils 50 %/40 %/25 %, once-per-combat, périodicité).
4. **Tests d'ordre d'évaluation** dans un hook (poison avant restore, etc.).
5. **Fixtures JSON** des exemples d'API dans `src/test/resources/battlesim/`, rejouées par un test
   paramétré.
6. **Test de couverture des effets (build rouge)** : compare `effects-snapshot.json`
   (codes `combat` + `consumable`) aux clés du `EffectRegistry` ; tout effet sans handler échoue.
   Pur, offline, **sans Testcontainers** (cf. incompatibilité Docker Desktop 29.x).

Mocks via **Mockito** (`MonsterRepository`, `ItemRepository`), conformément aux conventions projet.

## Hors périmètre (YAGNI phase 1)

- Bascule des appelants de `simulateWithApi` vers le moteur local (phase ultérieure).
- Fallback API en cas de donnée manquante.
- Modélisation des drops / gold / XP (le simulateur ne décide que de l'issue du combat).

## Risques / points à valider avec les exemples d'API

- **Ordre exact d'application des effets** dans un même hook (couvert par tests d'ordre).
- **Règle précise de consommation des potions** `heal` (seuil supposé 50 %, ajustable).
- **Ciblage du monstre en 3v1** (hypothèse 90 % threat / 10 % min HP).
- Effets boss/raid rares (`shell`, `guard`, `healing_aura`) : implémentés mais peu de fixtures
  disponibles → à valider quand des exemples existeront.

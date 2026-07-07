# Battle-sim comparison fixtures

Drop official API `/simulation/fight` results here as `example-<name>.json`:

```json
{
  "monster": { "code": "chicken", "hp": 60, "attackFire": 0, ... },   // full MonsterData fields
  "characters": [ { ...ArtifactsCharacter fields... } ],
  "runs": 10,
  "api": { "wins": 10, "losses": 0, "avgTurns": 4.0, "deterministic": true }
}
```

- `deterministic: true` when every character has critical_strike == 0 (no RNG) → exact match.
- Otherwise the test compares winrate within ±10 percentage points over 1000 runs.

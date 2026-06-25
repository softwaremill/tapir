# Schema inheritance scenario specs

This directory holds OpenAPI specs for exercising `openapiPackageDependencies` reuse vs redefine behaviour.
`Main.scala` wires cross-package assignments intended to **fail compilation** when aliasing is wrong.

## Baseline

| File | Package | Depends on | Intent |
|------|---------|------------|--------|
| `core.yaml` | `sttp.tapir.generated.core` | — | Canonical shared schemas |
| `v2.yaml` | `sttp.tapir.generated.v2` | — | Simple versioned baseline (parallel to core) |
| `v1.yaml` | `sttp.tapir.generated.v1` | v2 | Pet reuses v2; Order is v1-only |

## Full reuse (should alias to core)

| File | Package |
|------|---------|
| `reuse_all.yaml` | `reuse.all` |
| `reuse_enum.yaml` | `reuse.enum` |
| `reuse_nested.yaml` | `reuse.nested` |
| `reuse_oneof.yaml` | `reuse.oneof` |
| `reuse_map.yaml` | `reuse.map` |
| `reuse_array.yaml` | `reuse.array` |
| `reuse_simple_alias.yaml` | `reuse.alias` |
| `reuse_allof_pet.yaml` | `reuse.allof_pet` |
| `transitive_reuse.yaml` | `transitive.reuse` |

## Redefines (must NOT alias)

| File | Package | What differs |
|------|---------|--------------|
| `redefine_pet_extra_field.yaml` | `redefine.pet_extra` | Pet + `age` field |
| `redefine_pet_required.yaml` | `redefine.pet_required` | `species` required |
| `redefine_pet_type.yaml` | `redefine.pet_type` | `name` is `int64` |
| `redefine_pet_nullable.yaml` | `redefine.pet_nullable` | `species` nullable flag |
| `redefine_enum.yaml` | `redefine.enum` | Status + `PENDING` |
| `redefine_nested.yaml` | `redefine.nested` | Address requires postcode |
| `redefine_oneof.yaml` | `redefine.oneof` | Animal + Bird variant |
| `redefine_map.yaml` | `redefine.map` | TagMap values are int |
| `redefine_array.yaml` | `redefine.array` | IdList `uniqueItems` |
| `redefine_simple_alias.yaml` | `redefine.alias` | PetName is int |
| `redefine_default.yaml` | `redefine.default` | Order `note` default |
| `redefine_allof_pet.yaml` | `redefine.allof_pet` | allOf adds `vaccinated` |
| `redefine_shared_widget.yaml` | `redefine.shared_widget` | Widget + `version` |

## Mixed / transitive

| File | Package | Intent |
|------|---------|--------|
| `partial_reuse_order.yaml` | `partial.order` | Order matches; Pet has extra `tag` |
| `transitive_mismatch.yaml` | `transitive.mismatch` | Order matches; Pet missing `species` |

## Dependency chain

| File | Package | Depends on |
|------|---------|------------|
| `chain_mid.yaml` | `chain.mid` | core |
| `chain_leaf.yaml` | `chain.leaf` | chain.mid |

`LegacyPet` in leaf should alias to mid; must not alias to `core.Pet`.

## Shared consumers

| File | Package |
|------|---------|
| `shared_consumer_a.yaml` | `shared.a` |
| `shared_consumer_b.yaml` | `shared.b` |

Both reuse `Widget` from core; cross-consumer `Widget` assignment in `Main`.

## Directory merge (no package dependency)

| Directory | Package |
|-----------|---------|
| `merge/part1.yaml` + `merge/part2.yaml` | `merge` |

## Other

| File | Package | Intent |
|------|---------|--------|
| `inline_pet.yaml` | `inline.pet` | Named Pet schema matches core; endpoint uses inline body |

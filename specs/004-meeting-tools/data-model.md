# Data Model: Meeting Tools

Extends `specs/003-declared-connections/data-model.md`.

## §1 Connection (changed)

| Field | Type | Rules |
|---|---|---|
| `step` | integer, nullable | ≥ 1. Only with a flow (`flowId` not null). Same step = parallel. Joining a flow without a step → max step in that flow + 1. Leaving a flow, or the flow's deletion → null. |

## §2 Microservice (changed)

| Field | Type | Rules |
|---|---|---|
| `team` | string ≤ 120, nullable | Cleaned (trimmed, inner spaces collapsed); blank → null. |
| `repositoryUrl` | string ≤ 2048, nullable | Absolute `http`/`https` URL with a host. |
| `docsUrl` | string ≤ 2048, nullable | Same rules as `repositoryUrl`. |

## §3 Note (new)

| Field | Type | Rules |
|---|---|---|
| `id` | uuid | |
| `text` | string 1–2000 | Required, not blank. |
| `kind` | enum `NOTE`, `DECISION`, `QUESTION` | Default `NOTE`. |
| `position` | `{x, y}` | Required. |

## §4 Scenario content (additive)

- `ProposedConnection.step`: integer ≥ 1, nullable; only meaningful with `flowId`.
- `ConnectionChange.step`: integer ≥ 1, nullable (null = unchanged).
- `ProposedMicroservice.team`: string ≤ 120, nullable.
- `ScenarioContent.notes`: list of `ProposedNote {tempKey (starts with "proposed-note:"), text 1–2000, kind, position}`.

## §5 Connections document (additive, formatVersion 2)

- `connections[].step`: whole number ≥ 1; requires `flow` (else `STEP_WITHOUT_FLOW`); other values `INVALID_STEP`.
- `microservices[].team`, `.repository`, `.docs`: same absent/null rules as `description`; invalid URL → `INVALID_URL`; team too long → `TOO_LONG`.
- A connection listed with a flow and no step gets the next step (as in §1) when applied.

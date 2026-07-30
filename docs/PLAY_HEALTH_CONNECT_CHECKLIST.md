# Play Console — Health Connect data types (start here)

Complete **before closed testing** with real Health Connect reads (PR 4+).  
Final listing polish remains PR 9.

## Declare data types

In Play Console → App content / Health apps / Health Connect:

| Access | Data type | Purpose |
| --- | --- | --- |
| **Read** | Exercise | Identify swimming exercise sessions (pool / open water) |
| **Read** | Distance | Sum distance associated with those sessions for the calendar week |

**Do not declare** write access or History read for v1.

## Privacy policy

- Host a public privacy policy URL that matches in-app rationale (`PermissionsRationaleActivity`).
- State: on-device only, no network upload, read-only exercise + distance, optional background read.

## Data safety form

- Data collected: health & fitness (exercise, distance) — **processed on device**.
- Data shared: none (v1).
- Security practices: data not encrypted in transit (N/A — no network); users can request deletion by uninstall / revoke permissions (cache cleared on revoke in PR 4).

## Rationale activity

Manifest exports:

- `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
- `VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` (activity-alias)

Must show **static policy only** (no health records in that UI).

## Samsung path (product, not Play form)

Users enable **Samsung Health → Health Connect** export for exercise/distance.  
In-app bridge checklist ships in PR 5; lab-validate on Galaxy Watch 7 + S24 Ultra before beta copy freeze.

# AETHERON Free Developer Resources catalogue

## Source and attribution

The bundled catalogue is derived from the category index and service links in:

- Upstream project: https://github.com/ripienaar/free-for-dev
- Source file: `README.md`
- Upstream README blob at extraction: `a6242d69080c252b08938b5281276ec882b4201d`
- Catalogue extraction date: 2026-11-02

The upstream project describes itself as a community-maintained list of SaaS, PaaS, IaaS and other developer services with free tiers. Its inclusion rules and provider terms can change. AETHERON therefore links users to provider pages and treats the upstream free-tier notes as a reference, not a guarantee.

No LICENSE file was found in the upstream repository when this catalogue was generated. To avoid republishing the upstream README's descriptive prose, this data file contains service names, links, concise catalogue-generated descriptions, and short factual free-tier notes where present. Review the upstream repository's terms before copying its prose, branding or other assets.

## Data file

Path: `app/src/main/assets/free_for_dev_catalog.json`

Schema version: `1`

Top-level fields:

- `schemaVersion`: catalogue schema version.
- `source`: upstream repository, README blob SHA, attribution and licence note.
- `generatedAt`: date the snapshot was extracted.
- `categoryCount`: number of categories.
- `serviceCount`: total number of service and explicitly listed allowance entries.
- `categories`: categories with stable IDs, descriptions, upstream section URLs and service arrays.

Each service contains:

- `id`: stable category-local identifier.
- `name`: service or allowance name from the upstream list.
- `url`: provider URL; `stun:` endpoints are retained as endpoints.
- `urlType`: `website` or `endpoint`.
- `description`: AETHERON-generated catalogue description.
- `freeTierNotes`: short factual allowance/eligibility notes where the source states them. Empty means the source entry did not yield a concise note; it does not mean the provider has no free tier.
- `parentName`: parent provider for nested allowance entries, otherwise null.

## Validation

The Gradle task `validateFreeForDevCatalogue` parses the JSON and checks:

- schema version and required metadata;
- unique category IDs and category-local service IDs;
- non-empty service names, descriptions and supported URLs;
- declared category and service counts against actual entries.

The task is wired into `preBuild`, so a malformed or incomplete catalogue fails the Android build instead of silently shipping.

## Refresh procedure

1. Fetch the current upstream `README.md` from the repository's default branch.
2. Re-extract category sections and linked service names/URLs.
3. Preserve factual free-tier notes and update the README blob SHA and `generatedAt`.
4. Re-run `validateFreeForDevCatalogue` and the Android build.
5. Review changed providers and their current pricing pages before presenting any free-tier claim as current.

This first snapshot is intentionally bundled as a static asset. Automatic network syncing is a later feature and must not block offline browsing.

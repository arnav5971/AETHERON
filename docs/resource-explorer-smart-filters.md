# AETHERON Smart Filters (Step 4)

The Resource Explorer includes conservative filters for:

- **No credit card** — only entries whose catalogue text explicitly says a card/payment method is not required.
- **No signup** — only entries whose catalogue text explicitly says signup/registration/account creation is not required.
- **Always free** — only entries with explicit “always free”, “free forever”, or equivalent wording.
- **Limit listed** — entries with a recorded free-tier allowance, quota, quantity, or usage-limit note.
- **Cloud, AI / ML, API, Hosting** — category and service-keyword filters.

These filters are evidence-based, not provider guarantees. The catalogue is community-maintained and provider plans can change; users should confirm current eligibility, billing requirements, and limits on the provider's official site. Absence from a filter does not prove that a provider requires a card/signup or lacks an always-free tier.

## Verification

`ResourceFilterRulesTest` covers positive and negative matching for the four claim filters and the four service-type filters. The Android CI workflow runs the Gradle test tasks and debug APK build.

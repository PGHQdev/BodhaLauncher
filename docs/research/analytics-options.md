# Research: analytics options vs minimal in-house telemetry

Resolves #41 (part of #29). Constraint from DoD: no sensitive content in logs or general analytics. All claims verified against primary sources (vendor docs and pricing pages) on 2026-08-04; URLs inline. Caveats: Countly's support portal blocks direct fetches, so those claims were verified via search excerpts of the official articles (URLs still given); TelemetryDeck's paid prices are login-gated and could not be verified.

## Summary table

| Option | Default identifiers | EU residency | Self-host | Cost at <10k MAU / <1M ev | Android SDK | Play Data Safety guidance |
|---|---|---|---|---|---|---|
| Firebase Analytics | App-instance ID, **Advertising ID**, masked IP | No (no location choice for Analytics) | No | $0 | Vendor, mature; Play services "Recommended" | Yes — dedicated disclosure page |
| PostHog | Configurable; anonymous events possible; IP off by default on EU Cloud | Yes (Frankfurt) | Yes, but officially unsupported | $0 (1M ev/mo free) | Vendor, mature, offline queue | None found |
| Aptabase | None on device; server hashes IP+UA with daily salt | Yes (Germany) or US, chosen at signup | Yes (AGPL, Docker) | Free 20K ev/mo; $10/mo → 200K; $20 → 1M | Early (v0.0.8, JitPack), zero deps, **no offline queue** | Apple guide only |
| TelemetryDeck | Hashed+salted install ID; IPs never stored | EU-only (Hetzner Nuremberg) | No | Free 50K signals/mo (new accts); paid prices gated | v7.x on Maven Central, offline queue | Apple guide only |
| Countly | Random UUID; Advertising-ID fallback exists in config | Flex cloud is global GCP incl. US | Yes (Lite, AGPL-modified) | Self-host $0; hosted from $175/mo | Vendor, mature, consent API, offline queue | None found |
| Matomo | Random persisted visitor ID; IP anonymized by default server-side | Cloud is Frankfurt-only; On-Premise anywhere | Yes (GPL, free) | Cloud from €29/mo (50K hits); On-Premise $0 | Community-quality, maintained-but-slow | None found |
| In-house minimal (Workers+D1) | Whatever you choose (can be zero) | Your choice | You own it | ~$0–5/mo | You write it (~small) | Self-declared |
| On-device-only log | None leave device | n/a | n/a | $0 | trivial | Nothing to declare |

Plausible is web-only (script-tag pageview analytics, no mobile SDK); Aptabase is the mobile equivalent of that model and stands in for it here.

## Firebase (Google) Analytics

- Default collection: app-instance ID, Android Advertising ID, masked IP (coarse location), screen views/sessions, in-app purchase events — https://support.google.com/analytics/answer/11582702, https://support.google.com/firebase/answer/6318039
- Controls: `setAnalyticsCollectionEnabled()`, manifest flag for permanent deactivation, `allow_ad_personalization_signals`; Google recommends Consent Mode for honoring consent — https://firebase.google.com/docs/analytics/configure-data-collection
- Roles/DPA: Google is processor, you are controller; Data Processing and Security Terms apply — https://firebase.google.com/support/privacy
- Residency: no data-location selection for Analytics; the "reporting location" picker "does not affect where Google may process and store customer data" — https://firebase.google.com/docs/projects/locations. Data may be processed "anywhere Google or its agents maintain facilities" — https://firebase.google.com/support/privacy. No self-hosting.
- Cost: $0, no-cost on Spark and Blaze — https://firebase.google.com/pricing
- SDK: works without Play services but some automatic insights (demographics) need it ("Recommended" category) — https://firebase.google.com/docs/android/android-play-services. Events batched ~1 hour on-device — https://firebase.google.com/docs/analytics/debugview
- Play Data Safety: dedicated disclosure page — https://firebase.google.com/docs/android/play-data-disclosure, deferring for Analytics to https://support.google.com/analytics/answer/11582702 (declare app-instance ID, Advertising ID, coarse location from masked IP, purchases, interactions by default).
- Fit: worst match for the constraint — Advertising ID by default, no EU residency, ads-linked declaration.

## PostHog

- Anonymous events create no person profile; server SDKs support `$process_person_profile: false` — https://posthog.com/docs/data/anonymous-vs-identified-events. IP capture configurable; disabled by default on EU Cloud — https://posthog.com/docs/privacy/data-collection, https://posthog.com/docs/privacy/gdpr-compliance
- Roles/DPA: Cloud — PostHog is processor; self-serve countersigned DPA with SCCs — https://posthog.com/dpa
- Residency: EU Cloud in Frankfurt — https://posthog.com/docs/privacy/gdpr-compliance. Self-hosting is MIT/Docker but officially unsupported (no support, no versioning) — https://posthog.com/docs/self-host
- Cost: 1M events/mo free, then $0.00005/event tiered down — https://posthog.com/pricing. $0 at Bodha's scale.
- SDK: `com.posthog:posthog-android:3.+`, only `INTERNET` + `ACCESS_NETWORK_STATE` permissions, no Play services, offline queue with async batching — https://posthog.com/docs/libraries/android
- Play Data Safety: no vendor guidance found; you derive the declaration from your configuration.
- Fit: strong — full-featured, $0, EU hosting, anonymous-mode; heavier product than needed, and privacy depends on configuring it down.

## Aptabase

- No device identifiers collected by the SDK; server-side ID = SHA(client IP + UA + daily-rotated salt), salt discarded every 24h, so no cross-day correlation — https://aptabase.com/legal/privacy. Homepage: no cookies, no fingerprinting, no long-term identification — https://aptabase.com/
- Roles/DPA: you controller, Aptabase processor (Art. 28); DPA at https://aptabase.com/legal/dpa — https://aptabase.com/legal/privacy. No explicit "no consent banner needed" statement; the claim is anonymous-by-design framing.
- Residency: EU (Germany) or US (Virginia) chosen at signup — https://aptabase.com/. Self-hostable, AGPL-3.0 server / MIT SDKs, free via Docker — https://github.com/aptabase/aptabase, https://github.com/aptabase/self-hosting
- Cost: free 20K events/mo — https://aptabase.com/llms.txt; then $10/mo (200K), $20 (1M) — https://aptabase.com/#pricing. No overages; pauses at limit.
- SDK: `aptabase-kotlin` v0.0.8 via JitPack, zero external dependencies, manual events only — https://github.com/aptabase/aptabase-kotlin. Verified from source: no offline persistence — failed sends are dropped. Material gap for a launcher used offline.
- Play Data Safety: Apple-only guidance (Usage Data / Product Interaction, not linked, no tracking) — https://aptabase.com/docs/apple-app-privacy; translate to the Play form yourself.
- Fit: best privacy architecture of the hosted options; SDK immaturity and event loss offline are the trade.

## TelemetryDeck

- Anonymized install-constant user ID, double-hashed (client hash, then server salt+hash) — https://telemetrydeck.com/docs/guides/android-setup/. "IP addresses are never stored on the TelemetryDeck server"; vendor states no GDPR/CCPA-governed data is collected, no privacy-policy entry or opt-out needed — https://telemetrydeck.com/docs/guides/privacy-faq/ (their claim; you remain responsible for what you send). DPA published — https://telemetrydeck.com/dpa/
- Residency: EU-only, Hetzner Nuremberg (some Azure Amsterdam/AWS Frankfurt, consolidating to Hetzner in 2026); no self-hosting offered — https://telemetrydeck.com/use-case/architecture-security/
- Cost: free tier cut to 50K signals/mo for accounts created after 2026-07-01 — https://telemetrydeck.com/blog/pricing-update-2026/. Their ~30 signals/user/mo rule of thumb puts 50K at ~1,600 MAU; paid prices are behind a login-gated calculator (unverifiable).
- SDK: `com.telemetrydeck:kotlin-sdk` on Maven Central, v7.x, API 23+, offline signal queue persisted to app files, optional in-memory-only mode — https://github.com/TelemetryDeck/KotlinSDK
- Play Data Safety: Apple guide only (Identifiers + Usage Data, not linked, not tracking) — https://telemetrydeck.com/docs/articles/apple-app-privacy/; no Play page (verified against their sitemap).
- Fit: strong privacy posture and better SDK than Aptabase, but 10k MAU likely exceeds the new free tier and the paid price is opaque; no self-host exit.

## Countly

- Default device ID is a random SDK-generated UUID, but the config has an Advertising-ID fallback path you must deliberately avoid — https://support.countly.com/hc/en-us/articles/360037754031-Android, https://github.com/Countly/countly-sdk-android/blob/master/sdk/src/main/java/ly/count/android/sdk/CountlyConfig.java. Per-feature consent API (`setRequiresConsent(true)`, off by default) and temporary-device-ID mode — https://support.countly.com/hc/en-us/articles/11104014467737-Android-22-02
- Roles/DPA: Countly is processor, SCCs + UK addendum; sub-processors include US entities (GCP, OpenAI, Intercom) — https://countly.com/legal/dpa
- Residency: hosted Flex runs on GCP EU/US/Asia with US sub-processors — https://countly.com/legal/dpa. Self-hosted Lite is free, AGPL-3.0 with modified Section 7 (own-use only, no reselling) — https://github.com/Countly/countly-server, https://support.countly.com/hc/en-us/articles/360037501312-Countly-Lite-Licensing-FAQ
- Cost: Lite self-hosted $0 + server; hosted Flex from $175/mo, no free hosted tier — https://countly.com/pricing, https://countly.com/lite
- SDK: vendor-maintained, MIT, minSdk 21, persistent request queue, crash reporting, consent built in — https://github.com/Countly/countly-sdk-android
- Play Data Safety: no vendor guidance found.
- Fit: best-in-class SDK consent tooling, but hosted pricing is enterprise-shaped for this scale; only viable as self-hosted, and IP reaches your server on every request.

## Matomo (Cloud + On-Premise)

- IP anonymization on by default (2 bytes masked, configurable), cookieless option, raw-log auto-deletion, GDPR Manager for subject requests — https://matomo.org/faq/general/configure-privacy-settings-in-matomo/, https://matomo.org/gdpr/. Android SDK uses a random persisted visitor ID, no advertising ID — https://github.com/matomo-org/matomo-sdk-android
- Roles/DPA: Cloud — InnoCraft is processor under the Matomo Cloud DPA; On-Premise — you are sole controller, no third-party processor — https://matomo.org/faq/new-to-piwik/is-matomo-analytics-gdpr-compliant/
- Residency: Cloud on AWS Frankfurt, backups Ireland, "100% of your data and backups are securely stored in Europe"; InnoCraft is NZ (EU adequacy) — https://matomo.org/faq/in-which-locations-does-the-matomo-cloud-store-the-data/. On-Premise free, unlimited, anywhere — https://matomo.org/pricing/
- Cost: Cloud from €29/mo at 50K hits/mo; On-Premise $0 + server — https://matomo.org/pricing/
- SDK: `matomo-sdk-android`, BSD-3, JitPack, releases through Sep 2025 (v4.4) but largely dependency bumps; offline caching, WIFI-only dispatch — https://github.com/matomo-org/matomo-sdk-android/releases. Treat as maintained-but-slow and web-centric (hits model).
- Play Data Safety: no SDK guidance page; Matomo's own Play app declares no data sharing, but that's their app — https://play.google.com/store/apps/datasafety?id=org.piwik.mobile2
- Fit: excellent GDPR story (especially On-Premise), weakest mobile SDK of the vendor set, and pricing is web-hit-shaped.

## In-house minimal event pipeline

Shape: Kotlin client batches events into a local queue (Room/SQLite), periodic WorkManager flush to a single HTTPS endpoint (Cloudflare Worker) inserting into D1 (or ClickHouse/SQLite on a VPS). No third-party SDK.

- Cost: Workers free tier is 100K requests/day; D1 free tier is 5M rows read/day, 100K rows written/day, 5 GB — comfortably covers <10k MAU with batched uploads at $0. Paid plan is $5/mo with 10M requests included — https://developers.cloudflare.com/workers/platform/pricing/, https://developers.cloudflare.com/d1/platform/pricing/. A small VPS alternative runs roughly $4–6/mo (Hetzner's cheapest cloud tier; exact price not verified from a static page).
- GDPR: you are the sole controller, no processor, no vendor DPA needed (Cloudflare becomes an infrastructure sub-processor if you use Workers — sign Cloudflare's DPA). You choose exactly what leaves the device: random install ID or nothing; drop IPs at the edge before storage. This is the only option where "no sensitive content" is enforced by construction rather than configuration.
- Play Data Safety: "Collect" means transmitted off device, including by SDKs; on-device-only processing "does not need to be disclosed"; fully anonymized data is exempt but pseudonymous data (an install ID) must be declared under Analytics — https://support.google.com/googleplay/android-developer/answer/10787469. So: events sent to your endpoint → declare App interactions / Analytics, not shared; a purely on-device log → declare nothing collected.
- Cost in effort: the real price is building aggregation/dashboards yourself (a Worker + a few SQL queries covers counts and retention; anything more is your time).

## Recommendation considerations

- Every hosted option requires a Play Data Safety declaration; only a purely on-device log declares nothing. Decide first whether Bodha's positioning is "zero collection" (on-device log, optional user-triggered export) or "anonymous usage counts" (data leaves device, declared as not-linked analytics).
- If anonymous usage counts: **Aptabase (EU)** has the cleanest architecture (no device IDs, daily-rotating salt, DPA, self-host exit, $0–10/mo) but its Kotlin SDK is v0.0.8 with no offline queue — for a launcher that's a real loss, though the wire protocol is simple enough to wrap with your own WorkManager queue. **TelemetryDeck** is the same privacy class with a better SDK, but no self-host and opaque paid pricing past ~1,600 MAU. **PostHog EU** is $0 and mature but must be configured down to be private; its default product pulls toward identification.
- If control matters most: the **in-house Workers+D1 pipeline** at ~$0–5/mo gives sole-controller GDPR status and structurally caps what can be collected — the strongest match for the hard constraint, at the cost of building your own reporting.
- Firebase conflicts with the constraint (Advertising ID default, no EU residency). Countly hosted ($175/mo) and Matomo Cloud (web-hit model, legacy SDK) are poor fits at this scale; both remain viable only as self-hosted, at which point the in-house option is simpler.
- Practical hybrid: on-device event log now (nothing to declare), with a schema designed so a later opt-in flush to Aptabase or an own endpoint is a small change.

Gaps: no vendor except Firebase publishes Play Data Safety form guidance (Aptabase/TelemetryDeck cover Apple only); TelemetryDeck paid pricing and the exact cheapest VPS price could not be verified from primary sources.

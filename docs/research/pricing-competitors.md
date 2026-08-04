# Competitive pricing: intentional launchers & digital-wellbeing apps

- **Question:** What do comparable apps charge (monthly / annual / lifetime), what sits behind their paywalls, and how do trials work?
- **Date:** 2026-08-04
- **Method:** Primary sources only — official product sites, official US App Store listings (which enumerate in-app purchases with USD prices), Google Play listings, GitHub for OSS, official FAQ/help pages. Play Store pages returned truncated content on fetch, so Android-side IAP ranges are marked unverified where the description didn't state prices. App Store IAP lists often show several price points for the same tier name (legacy/experiment SKUs); ranges are reported as listed. All USD prices are US-region; several vendors localize prices, flagged per app.

---

## minimalist phone (Android + iOS)

Trial-gated, no permanent free tier of substance. iOS US App Store listing (developer shown as Hachi Media OÜ; the minimalistphone.com product) lists multiple price points per tier — likely A/B or legacy SKUs.

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Monthly | $2.99–$4.99 | All premium features |
| Annual | $9.99–$19.99 | Same |
| Lifetime | $29.99–$69.99 (modal $59.99) | Same, one-time |

- Paywalled: app blocker, website blocker, in-app time reminders, 15-second mindful launch delay, blocking schedules, custom home screen ([US App Store listing](https://apps.apple.com/us/app/minimalist-phone-block-apps/id6742103871)).
- Trial: 7 days on all plans; after trial a subscription is required (hard paywall). Official FAQ confirms monthly/annual/one-time structure but says prices "vary by region and local taxes — download the app to see them" ([faq.minimalistphone.com](https://faq.minimalistphone.com/en/article/what-is-the-price-of-minimalist-phone-how-much-will-i-pay-cygfyp/)); a separate FAQ explains billing kicks in after the 7-day trial ([FAQ](https://faq.minimalistphone.com/en/article/when-will-i-be-charged-for-buying-minimalist-phone-g7ijv2/)).
- FAQ explicitly addresses "why is it not free" ([FAQ](https://faq.minimalistphone.com/en/article/why-is-minimalist-phone-not-available-for-free-1jtds5m/)) — deliberate paid positioning.
- Android Play listing ([play.google.com](https://play.google.com/store/apps/details?id=com.qqlabs.minimalistlauncher)) IAP range could not be extracted (page truncated on fetch) — **unverified**; assume parity with iOS.
- Lifetime ≈ 3× annual (at $59.99 vs $19.99).

## Olauncher (Android, OSS)

| Tier | Price | Unlocks |
|---|---|---|
| Free | $0 | Everything |

- GPLv3, completely free, no ads, no IAPs; on Play, F-Droid, GitHub releases ([github.com/tanujnotes/Olauncher](https://github.com/tanujnotes/Olauncher)).
- Same developer monetizes via a separate paid app ("Pro Launcher" with widgets/weather/folders) rather than paywalling Olauncher.

## Niagara Launcher (Android)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Core launcher, wave alphabet, direct reply, ad-free |
| Pro annual | $13.99/yr | Themes, icon/font/clock customization, calendar+weather widget, pop-up folders, widget stacks, "usage breaker" |
| Pro lifetime | $42.99 one-time | Same |

- Prices from official help page; explicitly localized by country ("the price will be adapted" in low-income regions) ([help.niagaralauncher.app/article/104](https://help.niagaralauncher.app/article/104-price-of-niagara-pro), [price localization](https://help.niagaralauncher.app/article/139-price-localization)). Reference EU lifetime €39.99.
- No monthly tier. Freemium (soft paywall — free tier is fully usable). Subscription renewals stay at original price for early supporters ([subscription model article](https://help.niagaralauncher.app/article/85-subscription-model)).
- Lifetime ≈ 3.1× annual.

## Blank Spaces (iOS)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Monthly | $3.99 (also a $7.99 SKU listed) | Full app |
| Annual | $17.99–$19.99 | Full app |
| Lifetime | $23.99–$29.99 | Full app, one-time |
| Weekly | $1.99 | Full app |

- 7-day free trial, then subscription required (hard paywall) — launcher widget + distraction interruption are the product ([US App Store listing](https://apps.apple.com/us/app/blank-spaces-launcher/id1570856853)). Website shows no pricing ([blankspaces.app](https://www.blankspaces.app/)).
- Lifetime ≈ 1.3–1.7× annual — unusually cheap lifetime, positioned as the default buy.

## Opal (iOS, Android, Mac)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | 1 rule, basic timers/soundscapes, app blocking, daily Opal Score |
| Pro monthly | $19.99/mo | Unlimited rules, harder blocking (no unblocks), advanced timers, Allow Only mode, score history |
| Pro annual | $99.99/yr ($8.29/mo equiv.) | Same |
| Pro lifetime | $399 one-time | Same |

- Source: official pricing page ([opalapp.com/pricing](https://opalapp.com/pricing)); US App Store IAP list corroborates $19.99 monthly / $99.99 yearly, plus a $4.99 weekly SKU ([App Store](https://apps.apple.com/us/app/opal-screen-time-for-focus/id1497465230)).
- Trials: 7-day on monthly, 3-day on annual; none on lifetime. Freemium (soft paywall). 30-day money-back guarantee for Stripe (web) purchases — Opal sells both via App Store IAP and Stripe on the web.
- Lifetime ≈ 4× annual. Premium-priced outlier of the category.

## one sec (iOS + Android + browser)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Full intervention on **one** app of your choice |
| Pro annual | $19.99/yr | Unlimited apps, all interventions |
| Pro lifetime | $99.99 one-time | Same |
| Family annual / monthly / lifetime | $29.99/yr / $3.99/mo / $149 | Pro for the family |

- Source: US App Store IAP list and description ("Using one sec with one app of your choice is completely free") ([App Store](https://apps.apple.com/us/app/one-sec-screen-time-focus/id1532875441)). No solo monthly SKU on the US list; other storefronts show ~$5.99 monthly — region-dependent, unverified for US.
- Freemium, soft paywall. Team licenses "as low as 83ct per user per month, billed annually" ([one-sec.app](https://one-sec.app/)).
- Lifetime = 5× annual.

## Unpluq (iOS 16+ + Android; optional hardware Tag)

| Tier | Price | Unlocks |
|---|---|---|
| Subscription (3/6/12-month terms) | From ~$30 per term; exact per-term prices not published on fetched pages — **unverified** | Entire app: blocking, schedules, up to 7 "digital barriers", screen-time tracking |
| Hardware Tag | Sold with-Tag / without-Tag bundle options | NFC physical key to unlock blocked apps |

- "Premium subscription required for use" — no free tier; hard paywall ([unpluq.com](https://www.unpluq.com/)). 30-day money-back guarantee. /pricing returned 404 at fetch time; exact tier prices are region/checkout-dependent — treat as unverified.

## Brick (hardware + free app, iOS 17+ / Android 12+)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Brick device | $59 one-time | Physical NFC blocker + full app forever |
| App subscription | None | — |

- "No subscriptions, no extra fees. Buy once, use forever." Includes 5 emergency unbricks; 30-day money-back guarantee; HSA/FSA eligible ([getbrick.com](https://getbrick.com/), redirect target of getbrick.app).
- The anti-subscription stance is the marketing wedge.

## Jomo (iOS)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Core blocking with limits |
| Plus monthly | $5.99/mo | Unlimited recurring sessions, strict mode, advanced rules, analytics, iPad version |
| Plus annual | $29.99/yr ($2.49/mo equiv.) | Same; 3-day trial |
| Plus lifetime | $84.99–$99.99 one-time | Same |
| Family | $11.99/mo / $59.99/yr | Plus for family |

- Source: US App Store IAP list ([App Store](https://apps.apple.com/us/app/jomo-screen-time-blocker/id1609960918)). Freemium, soft paywall; trial only on annual.
- Lifetime ≈ 3.3× annual.

## ClearSpace (iOS)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Basic blocking/limits, focus scheduling, challenges |
| Premium monthly | $6.99/mo | Advanced features (listing doesn't itemize) |
| Premium annual | $44.99–$59.99/yr (multiple SKUs) | Same |
| Family | $79.99/yr | Same |

- Source: US App Store IAP list ([App Store](https://apps.apple.com/us/app/clearspace-reduce-screen-time/id1572515807)). Free-for-students program (school email until graduation). Free trial referenced; length not stated on listing. No lifetime SKU.

## ScreenZen (iOS + Android)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Everything |
| Tips | $5 / $10 / $20 / $40 | Nothing — donations |

- Self-described "the only FREE, donation-supported screentime app blocker"; IAPs are tips only ([US App Store listing](https://apps.apple.com/us/app/screenzen-screen-time-control/id1541027222)). The category's free anchor.

## Freedom (iOS, Android, macOS, Windows, Chromebook)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Basic blocking |
| Premium monthly | $8.99/mo | Scheduling, recurring/advance sessions, Locked Mode, unlimited sessions (2h+), perks |
| Premium yearly | $3.33/mo billed annually (~$39.96/yr) | Same; 7-day trial |
| Forever (lifetime) | $99.50 (promo "50% off" from $199 list) | Same, one-time |

- Source: official upgrade page ([freedom.to/upgrade](https://freedom.to/upgrade)). Freemium, soft paywall. The "Forever" price runs on a seemingly permanent 50%-off promo.
- Lifetime ≈ 2.5× annual at promo price (5× at list).

## Forest (iOS; Android version separate)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free (iOS listing now free) | $0 | Core focus timer |
| Forest Plus monthly | $5.99/mo ("Early Bird") | 3x coins, exclusive trees, real-tree planting |
| Forest Plus annual | $32.49–$35.99/yr | Same; free trial offered |
| Cosmetic IAPs | $0.99–$21.99 | Decorations/currency |

- Source: US App Store listing ([App Store](https://apps.apple.com/us/app/forest-focus-for-productivity/id866450515)). Adjacent category (gamified focus timer) — included as a pricing reference point only.

## Roots (iOS)

| Tier | Price (USD) | Unlocks |
|---|---|---|
| Free | $0 | Basic blocking, time limits, tracking, daily goals |
| Plus weekly | $7.99/wk | Monk Mode, dopamine monitoring, detox challenges, adult-site blocking, trends, streaks |
| Plus monthly | $9.99/mo | Same |
| Plus annual | $59.99/yr | Same |
| Premium passes (one-time SKUs) | $19.99–$99.99 | Same (legacy/one-time variants) |

- Source: US App Store IAP list ([App Store](https://apps.apple.com/us/app/roots-screen-time-control/id6446800962)). Prices heavily localized by region. Offers reduced-payment form for low-income users. Note the aggressive $7.99 weekly SKU.

## Before Launcher (Android)

- Freemium launcher by Before Labs; premium is a **one-time lifetime purchase** ("never requires a subscription, lifetime purchase for all your devices") unlocking themes, hiding bloatware, etc. ([Play listing](https://play.google.com/store/apps/details?id=com.beforesoft.launcher&hl=en_US)). Exact premium price not extractable from the fetched Play page — **unverified** (historically ~$4–10 one-time; secondary, unconfirmed).

## Dumbify (iOS + Android)

- iOS: paid-upfront one-time apps — [Dumbify $4.99](https://apps.apple.com/us/app/dumbify/id6480082872) and a newer [Dumbify – Minimal Launcher $0.99](https://apps.apple.com/us/app/dumbify-minimal-launcher/id6755948787). No subscription. Prices seen via App Store search snippets of the official listings; the listings themselves are primary.

## Ratio (Blloc, Android)

- Effectively dormant: Play listing still up but Blloc paused invites/BllocDesk; no active pricing signal ([blloc.com/faq](https://www.blloc.com/faq)). Skipped from comparison.

---

## Cross-app comparison (USD, US region)

| App | Platform | Monthly | Annual | Lifetime | Free tier | Trial |
|---|---|---|---|---|---|---|
| minimalist phone | Android + iOS | $2.99–4.99 | $9.99–19.99 | $29.99–69.99 | None meaningful | 7d, hard |
| Olauncher | Android | — | — | free (OSS) | Everything | — |
| Niagara | Android | — | $13.99 | $42.99 | Full core launcher | Freemium |
| Blank Spaces | iOS | $3.99 | $17.99–19.99 | $23.99–29.99 | Trial only | 7d, hard |
| Opal | iOS/Android/Mac | $19.99 | $99.99 | $399 | 1 rule, basics | 3–7d, soft |
| one sec | iOS/Android/web | (no US solo monthly) | $19.99 | $99.99 | 1 app, full | Freemium |
| Unpluq | iOS + Android | ~from $30/3mo (unverified) | term-based | — | None | Hard |
| Brick | iOS + Android | — | — | $59 hardware, app free | Full app w/ device | — |
| Jomo | iOS | $5.99 | $29.99 | $84.99–99.99 | Core blocking | 3d (annual), soft |
| ClearSpace | iOS | $6.99 | $44.99–59.99 | — | Basic blocking | yes (len n/a), soft |
| ScreenZen | iOS + Android | — | — | free (tips $5–40) | Everything | — |
| Freedom | 5 platforms | $8.99 | ~$39.96 | $99.50 promo ($199 list) | Basic blocking | 7d, soft |
| Roots | iOS | $9.99 | $59.99 | — ($19.99–99.99 one-time passes) | Basic blocking | soft |
| Forest | iOS | $5.99 | $32.49–35.99 | — | Core timer | yes, soft |
| Before Launcher | Android | — | — | one-time (price unverified) | Core launcher | Freemium |
| Dumbify | iOS | — | — | $0.99–4.99 paid-upfront | — | — |

## Patterns

- **Annual price band:** launchers cluster at **$10–20/yr** (minimalist phone, Niagara, Blank Spaces, one sec); blocker/wellbeing apps at **$30–60/yr** (Jomo, Forest, Freedom, ClearSpace, Roots); Opal is the $100/yr premium outlier. Weekly SKUs ($1.99–7.99) exist as high-ARPU experiments (Blank Spaces, Opal, Roots).
- **Lifetime as multiple of annual:** typically **3–5×** (Niagara 3.1×, minimalist phone ~3×, Jomo 3.3×, Opal 4×, one sec 5×). Blank Spaces is the anomaly at ~1.3–1.7×, using lifetime as the conversion default. Several strong players (ClearSpace, Roots, Forest) skip lifetime entirely.
- **Hard vs soft paywall:** launchers whose whole product is the experience (minimalist phone, Blank Spaces, Unpluq) run **trial-then-hard-paywall** (7-day norm). Blocker apps mostly run **freemium with a capped free tier** — the cap is usually a count: 1 rule (Opal), 1 app (one sec), limited sessions (Freedom, Jomo). Trials on subscriptions: 7d monthly / 3d annual is a recurring pattern (Opal, Jomo).
- **Free anchors:** Olauncher (OSS) and ScreenZen (donations) put a $0 floor under the category; Niagara's generous free tier does the same for Android launchers.
- **Hardware plays** (Brick $59, Unpluq Tag) monetize once and market "no subscription" as the differentiator (Brick) or still require a subscription anyway (Unpluq).
- **Regional pricing** is explicit at Niagara (documented localization policy), Roots, minimalist phone, one sec — USD figures above are US-region only.
- **Monetization stack:** all iOS apps sell through App Store IAP; Opal additionally sells via Stripe on the web (30-day refund only there). No RevenueCat/Adapty usage was verifiable from primary sources — not discoverable without inspecting app binaries.
- **SKU sprawl:** US App Store IAP lists routinely show 2–4 price points per tier (minimalist phone lifetime $29.99–69.99, ClearSpace annual $44.99–59.99) — evidence of live price testing/legacy grandfathering; quote ranges, not single prices, when benchmarking.

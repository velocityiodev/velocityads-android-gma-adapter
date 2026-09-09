# Velocity Ads – Google Mobile Ads Adapter (Android)

This library is the official Google Mobile Ads **custom event adapter** for the Velocity Ads Android SDK. It lets AdMob and Google Ad Manager mediate Velocity Ads demand alongside all other networks in your waterfall with zero boilerplate in your app.

---

## Supported ad formats

| Format | Supported |
|---|---|
| Interstitial | ✅ |
| Rewarded | ✅ |
| Banner / MREC / Leaderboard / Adaptive banner | ✅ |

---

## Requirements

| Requirement | Minimum version |
|---|---|
| Android | API 24 (Android 7.0) |
| Google Mobile Ads SDK | 24.x |
| Velocity Ads SDK | 0.10.1 |
| Kotlin | 2.0+ |

---

## Installation

Add the adapter to your app's `build.gradle`:

```groovy
dependencies {
    // Google Mobile Ads SDK (already present in most apps) — 24.4.0 is the version this
    // adapter is built and tested against; any 24.x release is compatible.
    implementation 'com.google.android.gms:play-services-ads:24.4.0'

    // Velocity Ads SDK
    implementation 'io.velocity:ads-sdk:0.10.1'

    // Velocity Ads GMA Adapter
    implementation 'io.velocity:gma-mediation:0.10.1.0'
}
```

Both `google()` and `mavenCentral()` must be in your repository list (they are in every default Android Studio project).

---

## AdMob / Ad Manager Setup

### Step 1 – Create a Custom Event

1. In the AdMob UI, go to **Mediation → Waterfall sources → Custom events** (or in Ad Manager: **Delivery → Yield groups → Custom event**).
2. Click **Add custom event** and set:
   - **Label**: `Velocity Ads`
   - **Class Name**: `io.velocityads.gma.VelocityAdsGmaAdapter`
   - **Parameter**: see below

### Step 2 – Configure the parameter

The **Parameter** field carries the Velocity configuration for the mapping as a JSON object:

```json
{"appKey":"YOUR_VELOCITY_APP_KEY","adUnitId":"YOUR_VELOCITY_AD_UNIT_ID"}
```

- `adUnitId` (required) — the Velocity ad unit ID for this placement.
- `appKey` (recommended) — your Velocity app key. Include it on every mapping so the adapter can initialize the Velocity SDK on its own. Use **one Velocity app key per application process** across all mappings.

If your app already initializes the Velocity SDK directly, `appKey` may be omitted and the parameter can be just the bare ad unit ID string.

### Step 3 – Add Velocity Ads to your ad units

Add the custom event to the mediation group / yield group of each ad unit you want Velocity Ads to fill. For rewarded ad units, configure the reward amount and type in the AdMob UI — the adapter delivers the reward the Google Mobile Ads SDK is configured with.

---

## SDK Initialization

The adapter initializes the Velocity SDK automatically. You do **not** need to call `VelocityAds.initSDK()` yourself.

- When the Google Mobile Ads SDK initializes its adapters (`MobileAds.initialize()`), the adapter initializes the Velocity SDK using the first `appKey` found across your custom event mappings.
- If no mapping carries an `appKey`, the adapter reports ready immediately and initializes the Velocity SDK lazily on the first ad request that does carry one — or relies on your app having initialized the SDK directly.
- If the Velocity SDK is already initialized by your app, the adapter detects this and skips initialization.

Concurrent initialization attempts are coalesced; the Velocity SDK is initialized at most once per process.

---

## Privacy & Consent

**GDPR / TCF** — The Velocity SDK reads the IAB TCF v2 consent signals (`IABTCF_gdprApplies`, `IABTCF_TCString`) directly from `SharedPreferences`. Google's User Messaging Platform (UMP) SDK and every IAB-registered CMP write these keys, so no additional integration is required.

**CCPA / US privacy** — The Google Mobile Ads SDK does not expose a per-request "do not sell" signal to adapters. If you need to forward a CCPA opt-out, call the Velocity SDK directly from your app:

```kotlin
VelocityAds.setDoNotSell(true) // user opted out of sale of personal data
```

---

## Banner sizes

| Google Mobile Ads `AdSize` | Velocity size |
|---|---|
| `BANNER` (320×50) | Banner |
| `MEDIUM_RECTANGLE` (300×250) | MREC |
| `LEADERBOARD` (728×90) | Leaderboard |
| Anchored adaptive (`getCurrentOrientationAnchoredAdaptiveBannerAdSize`) | Adaptive — served at the exact width × height requested |
| Inline adaptive / other fixed sizes | Served at the exact width × height requested |
| `FLUID` | Not supported — the request fails and the waterfall advances |

---

## Error reporting

Load and show failures are surfaced as standard `AdError` values:

- Errors raised by the Velocity SDK use the domain `io.velocityads.sdk`. The top-level code is the closest `AdRequest.ERROR_CODE_*` category (no fill, network, invalid request, internal); the original Velocity error code and message are attached as the `cause` and are visible in Ad Inspector.
- Errors detected by the adapter itself (missing configuration, SDK not initialized, ad not ready, unsupported size) use the domain `io.velocityads.gma`.

---

## Version history

| Adapter version | Velocity SDK version | Notes |
|---|---|---|
| 0.10.1.0 | 0.10.1 | Initial release |

---

## License

Apache License 2.0 – see [LICENSE](LICENSE).

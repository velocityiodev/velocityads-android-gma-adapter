# AGENTS.md — velocityads-android-gma-adapter

Engineering guide for contributors and coding agents working on the Velocity Ads Google Mobile Ads adapter for Android.

---

## ⚠️ This is a public repository

This repository is publicly visible. Every file in it — `README.md`, `CHANGELOG.md`, source code comments, commit messages, and any other documentation — can be read by anyone, including publishers, competitors, and the general public.

### What must never appear in this repo

- Internal repository names (e.g. SDK internal repos, internal tooling repos).
- Internal field names, API paths, server endpoints, or request/response structures that are not part of the public SDK surface.
- Roadmap information: future mediation platforms, future adapter plans, or any unreleased product direction.
- Naming convention strategy documents or internal architecture decisions.
- CI secrets, credentials, or values — and any prose about them (formats, provenance, where a key is reused). The workflow YAML already validates and names the secrets it needs; do not duplicate that in documentation.
- References to internal tools, dashboards, or services not accessible to publishers.
- Internal SDK bridge APIs (e.g. mediation/plugin bridge seams) and how telemetry or analytics are attributed. Publishers integrate through the Google Mobile Ads SDK; they do not need to know how the adapter talks to the SDK internally.
- Maintainer release runbooks. The step-by-step release procedure lives in the team's internal documentation, not in this repo. The workflow's dispatch inputs are self-describing for anyone with permission to run it.

### What belongs here

- `README.md` — **publisher-facing only**: how to add the adapter, configure the AdMob custom event, handle privacy, supported formats, version compatibility. No maintainer or release content.
- Adapter behaviour documentation (initialization, ad formats, error handling).
- Public-facing `CHANGELOG.md` entries describing user-visible changes.

### Rule for coding agents

Before writing or editing any file that will be committed to this repo, ask: *could a publisher or external developer read this and learn something we did not intend to disclose?* If yes, rewrite or omit it.

---

## Project overview

This library is the official Google Mobile Ads **custom event adapter** that bridges the Velocity Ads Android SDK (`io.velocity:ads-sdk`) into the AdMob / Google Ad Manager mediation waterfall.

- **Repository**: `velocityads-android-gma-adapter` (public)
- **Published artifact**: `io.velocity:gma-mediation` on Maven Central
- **Version scheme**: 4-segment (`<sdkMajor>.<sdkMinor>.<sdkPatch>.<adapterBuild>`) — the 4th segment increments for adapter-only fixes against the same SDK version
- **Minimum Android SDK**: API 24
- **Supported Google Mobile Ads SDK**: 24.x

---

## Module layout

```
velocityads-android-gma-adapter/
├── velocity-gma-adapter/ # The adapter library module (published AAR)
│ └── src/main/java/io/velocityads/gma/
│ ├── VelocityAdsGmaAdapter.kt # Core adapter: initialize, versions, load entry points, init coalescing
│ ├── AdLoadContext.kt # Seam injected into the per-format ad classes
│ ├── VelocityAdsServerParameters.kt # Parses the custom event "parameter" string
│ ├── VelocityGmaInterstitialAd.kt # MediationInterstitialAd: owns the Velocity ad for one load cycle
│ ├── VelocityGmaRewardedAd.kt # MediationRewardedAd: owns the Velocity ad for one load cycle
│ ├── VelocityGmaBannerAd.kt # MediationBannerAd: owns the Velocity ad + view, size resolution
│ ├── VelocityInterstitialAdHandler.kt # Translates Velocity callbacks → GMA interstitial callbacks
│ ├── VelocityRewardedAdHandler.kt # Translates Velocity callbacks → GMA rewarded callbacks
│ ├── VelocityBannerAdHandler.kt # Translates Velocity callbacks → GMA banner callbacks
│ ├── VelocityAdsErrorMapper.kt # Builds AdError values (adapter + SDK domains)
│ ├── VersionInfoParser.kt # Adapter / SDK version strings → VersionInfo
│ └── InitCoalescer.kt # Coalesces concurrent init attempts
├── build.gradle # Root build: Nexus publish plugin + ktlint plugin
├── gradle.properties # VERSION_NAME, GROUP, ARTIFACT_ID
└── .github/workflows/
 ├── tests.yml # CI: ktlint + unit tests on PR/push to main
 └── release.yml # Release: validate → build+publish → tag+GitHub Release
```

---

## Adapter class name

The class registered in the AdMob **Custom event** entry is:

```
io.velocityads.gma.VelocityAdsGmaAdapter
```

Do not rename this class — it is a hard-coded string in every publisher's AdMob configuration, and `consumer-rules.pro` keeps it from being renamed by R8 in consuming apps.

---

## Custom event parameter contract

The Google Mobile Ads SDK delivers one opaque string per custom event mapping. `VelocityAdsServerParameters` accepts:

- a JSON object `{"appKey":"…","adUnitId":"…"}` (`appKey` optional), or
- a bare string, treated as the ad unit ID.

Do not add new keys without updating `README.md`; the parameter is publisher-facing configuration.

---

## Versioning

Version source of truth: `VERSION_NAME` in `gradle.properties`. It is compiled into `BuildConfig.ADAPTER_VERSION`.

The version follows the 4-segment convention `<sdkMajor>.<sdkMinor>.<sdkPatch>.<adapterBuild>`. Git tags use the same 4-segment string (e.g. `0.10.0.0`). `getVersionInfo()` reports it to Google as `major.minor.(patch * 100 + adapterBuild)`, matching Google's mediation adapter convention.

---

## Build & verification

```bash
# Fast check — ktlint + unit tests only (~1–2 min)
./gradlew :velocity-gma-adapter:build

# Verify ktlint formatting only
./gradlew :velocity-gma-adapter:ktlintCheck

# Auto-fix ktlint violations
./gradlew :velocity-gma-adapter:ktlintFormat
```

---

## CI workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `tests.yml` | PR / push to `main` | ktlint check + full build + unit tests |
| `release.yml` | Manual dispatch | Validate versions → build + publish to Maven Central → GPG-signed tag + GitHub Release |

The `env:` block at the top of each workflow file contains all adapter-specific values (module name, artifact ID). All other steps are mediation-agnostic.

---

## Kotlin conventions

Follow the conventions from the Velocity Ads Android SDK `AGENTS.md`:

- Kotlin-first; all new code in Kotlin.
- `internal` for anything not part of the public adapter surface. The only public type is `VelocityAdsGmaAdapter`.
- No `e.printStackTrace()` — use `Log.e(TAG, message, e)`.
- Coroutines for async work; no raw `Thread`.
- Code must pass `ktlintCheck` before merging.

---

## Changelog convention

Follow [Keep a Changelog](https://keepachangelog.com). Use `### Added`, `### Changed`, `### Fixed`, `### Breaking Changes` as section headings. Write for publishers — describe user-visible behaviour, not internal implementation.

---

## Code hygiene

Delete everything that no longer reflects the current state of the codebase:

- Dead code and unused imports.
- Stale comments or narration-only comments.
- Any reference to internal repos, tools, or field names (see the **Public repository** section above).

---

## Pre-merge checklist

1. `./gradlew :velocity-gma-adapter:build` passes (ktlint + unit tests).
2. No internal repo names, field names, key material details, bridge APIs, or roadmap content in any committed file.
3. `CHANGELOG.md` updated if the change is user-visible.
4. `README.md` updated if the public-facing integration instructions changed.

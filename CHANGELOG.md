# Changelog

## [0.10.0.0] - 2026-09-08

### Added

- Initial release of the Velocity Ads Google Mobile Ads (AdMob / Ad Manager) custom event adapter for Android.
- Interstitial, rewarded and banner ad formats, including MREC, leaderboard and adaptive banner sizes.
- Automatic Velocity SDK initialization from the custom event parameter, with lazy initialization on first load when no app key is configured at startup.
- Velocity SDK error codes and messages preserved on every reported `AdError`.
- Banner creatives are released automatically once the Google Mobile Ads SDK removes the banner view (refresh or `AdView.destroy()`).

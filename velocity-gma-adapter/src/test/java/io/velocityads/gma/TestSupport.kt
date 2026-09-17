package io.velocityads.gma

import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import org.mockito.ArgumentCaptor

internal typealias InterstitialLoadCallback = MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>
internal typealias RewardedLoadCallback = MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>
internal typealias BannerLoadCallback = MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>

/**
 * [ArgumentCaptor.capture] returns `null` during verification, which Kotlin rejects when the
 * verified method declares a non-null parameter. The unchecked cast sidesteps that check.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> ArgumentCaptor<T>.captureNonNull(): T = capture() as T

# The Google Mobile Ads SDK loads this adapter by its fully-qualified class name via
# reflection (the class name configured in the AdMob custom event). It must survive
# R8/ProGuard in the consumer app's build.
-keep class io.velocityads.gma.VelocityAdsGmaAdapter { *; }

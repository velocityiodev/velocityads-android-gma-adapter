# Keep the adapter class name for Google Mobile Ads reflection loading.
# NOTE: minifyEnabled is false for this library module, so these rules are NOT applied
# during the library's own R8 pass. The equivalent rule in consumer-rules.pro IS applied
# during the consuming app's build, which is where the GMA SDK's reflection loading happens.
-keep class io.velocityads.gma.VelocityAdsGmaAdapter { *; }

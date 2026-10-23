# Android components and callback methods are framework entry points.
# Preserve the small camera implementation; shrink unused Kotlin runtime code.
-keep class com.hdrg.camera.** { *; }
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**
-keepattributes Signature,InnerClasses,EnclosingMethod

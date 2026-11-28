# Keep line numbers & source files for readable Play Console stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# @Keep annotations
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

# Domain models & enums (Firestore serialization / Kotlin data models)
-keep class com.example.domain.** { *; }
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Firebase Firestore & Auth
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# AndroidX Credentials & Google Sign-In
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# Phase 16A: Google Mobile Ads SDK (AdMob) & Google UMP Consent SDK
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keepclassmembers class * extends com.google.android.gms.ads.mediation.MediationAdapter {
    public <init>();
}
-keep public class com.google.android.gms.ads.MobileAdsInitProvider { *; }
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.ump.**

# Phase 16B: Google Play Billing Library & Firebase Functions
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }
-keep class com.google.firebase.functions.** { *; }
-dontwarn com.android.billingclient.**



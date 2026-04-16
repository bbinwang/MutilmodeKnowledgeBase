# Add project specific ProGuard rules here.

# ObjectBox
-keep class com.multimode.kb.data.local.objectbox.** { *; }
-keepclassmembers class ** {
    @io.objectbox.annotation.** <fields>;
}
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Apache POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }

# PDFBox
-dontwarn org.apache.pdfbox.**

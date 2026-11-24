# --- Vosk y JNA (puente nativo JNI) ---
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# --- Google AI Edge LiteRT-LM (motor local .litertlm) ---
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Coroutines & Credentials ---
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.credentials.**

# --- Atributos genéricos y el SDK de Google Identity ---
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.errorprone.annotations.**

# Library-specific rules (Room, kotlinx.serialization, Ktor, Coil) ship as
# consumer rules inside their AARs; only app-owned reflective surfaces are here.

# kotlinx.serialization: serializers for app models are looked up via Companion.
-keepclassmembers class app.splitup.** {
    *** Companion;
}
-keepclasseswithmembers class app.splitup.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.slf4j.**

# Tink (via androidx.security-crypto, used by SecretStore) references ErrorProne
# annotations that are compile-time only and absent from the runtime classpath.
-dontwarn com.google.errorprone.annotations.**

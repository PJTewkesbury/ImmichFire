-keepattributes *Annotation*, InnerClasses
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.intec.immichfire.**$$serializer { *; }
-keepclassmembers class com.intec.immichfire.** {
    *** Companion;
}
-keepclasseswithmembers class com.intec.immichfire.** {
    kotlinx.serialization.KSerializer serializer(...);
}

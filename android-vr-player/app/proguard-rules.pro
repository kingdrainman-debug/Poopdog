# Media3 ships its own consumer rules. Keep our GL effect classes (instantiated via
# Media3's effect pipeline) and enum names used for persisted settings.
-keep class com.poopdog.vr.convert.** { *; }
-keepclassmembers enum com.poopdog.vr.** { *; }

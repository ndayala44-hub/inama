# Inama uses no reflection-based serialization (custom JSON codec in data/json), so no keep rules
# are needed for models. OkHttp, Coil and CameraX ship their own consumer rules.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

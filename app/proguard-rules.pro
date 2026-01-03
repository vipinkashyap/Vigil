# Vigil ProGuard Rules

# Keep RootEncoder classes
-keep class com.pedro.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.** { *; }

# Keep JmDNS
-keep class javax.jmdns.** { *; }

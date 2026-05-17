# Suppress duplicate class notes from kotlin-stdlib-jdk7/jdk8 overlapping with main stdlib
-dontnote **

# Keep @Serializable data classes so ProGuard doesn't strip their descriptor
-keep @kotlinx.serialization.Serializable class ** { *; }
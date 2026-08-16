# ProGuard rules for Android
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
# Keep the JNI wrapper class and its methods
-keep class com.example.homecil.PaperEngine { *; }
-keep class com.example.homecil.PaperEngine$** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
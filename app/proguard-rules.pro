# Add project specific ProGuard rules here.

# Keep source file names and line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signature of Call, Response (R8 full mode)
-keepattributes Signature

# Keep annotation for runtime
-keepattributes *Annotation*

# Keep exceptions
-keepattributes Exceptions

# Kotlin metadata — required for Hilt constructor injection and coroutines
-keepattributes kotlin.Metadata

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Firebase
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}
-keep class com.google.firebase.** { *; }

# Gemini AI
-keep class com.google.ai.client.generativeai.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.budgetapp.hilt_aggregated_deps.** { *; }

# Keep all ViewModels by their real names so R8 can't rename two to the same
# obfuscated name (causes "Multiple entries with same key" in HiltViewModelFactory).
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Hilt-generated component and factory classes in app package
-keep class com.budgetapp.**_Hilt* { *; }
-keep class com.budgetapp.**_Factory { *; }
-keep class com.budgetapp.**_MembersInjector { *; }
-keep class com.budgetapp.Hilt_* { *; }

# Kotlin coroutines StateFlow / SharedFlow internal state classes
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.flow.** { *; }

# Keep data classes for Firestore
-keep class com.budgetapp.data.** { *; }
-keep class com.budgetapp.domain.model.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Apache POI (Excel parsing)
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**

# Security
-keep class androidx.security.crypto.** { *; }

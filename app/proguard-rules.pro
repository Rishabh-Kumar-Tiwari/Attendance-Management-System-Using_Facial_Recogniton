-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.example.attendancemanagementsystem.** { *; }

-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

-keep class com.google.mlkit.** { *; }
-keepclassmembers class com.google.mlkit.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }

# The app is not minified in release builds, but keep the rules valid for
# anyone who enables R8 later.
-keep class com.moonlight.bedrockclient.ui.** { *; }
-keepclassmembers class com.moonlight.bedrockclient.core.** { *; }

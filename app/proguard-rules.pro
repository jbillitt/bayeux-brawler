# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so a Play crash report deobfuscates to a real stack trace, and rename the
# source file so the original names are still not shipped. Without these an obfuscated crash is
# a wall of a/b/c with no line to go to.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The art pipeline loads vector JSON by asset name at runtime (VectorAsset). Nothing here is
# reflected into by name, but the enum entries ARE matched to those asset ids, and R8's enum
# optimisations are the classic way that silently stops resolving.
-keepclassmembers enum com.example.game.** { *; }

# The ads and billing SDKs ship their own consumer rules; these two are the ones their docs ask
# the host app to add, because both are reached through Play services rather than from our code.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }

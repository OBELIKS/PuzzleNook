# --- Onyx Boox pen SDK ---------------------------------------------------------
# The SDK talks to the e-ink framework via reflection, EventBus and fastjson, none of
# which R8 can trace. It only runs on Boox devices, where we can't test a shrunk build
# easily, so keep it whole rather than risk a stripped class at runtime.
-keep class com.onyx.** { *; }
-keep interface com.onyx.** { *; }
-dontwarn com.onyx.**

# Used by the Onyx SDK
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keep class com.alibaba.fastjson2.** { *; }
-dontwarn com.alibaba.fastjson2.**
-keep class com.tencent.mmkv.** { *; }
-dontwarn org.greenrobot.eventbus.**
-dontwarn io.reactivex.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.nustaq.**
-dontwarn org.apache.commons.**
-dontwarn net.lingala.zip4j.**
-dontwarn org.joda.time.**

# Hidden-API bypass used to unlock the Onyx SDK on Android 9+
-keep class org.lsposed.hiddenapibypass.** { *; }

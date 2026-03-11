// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    alias(libs.plugins.googleService) apply false
    alias(libs.plugins.crashlytics) apply  false

    //dragger - Hilt
    id("com.google.dagger.hilt.android") version "2.48" apply false

    //safe args
    id("androidx.navigation.safeargs.kotlin") version "2.5.3" apply false

}
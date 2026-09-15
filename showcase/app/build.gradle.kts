plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.carsystemui.showcase"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.carsystemui.showcase"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "ATEP_BASE_URL",
            quotedBuildConfig(providers.gradleProperty("ATEP_BASE_URL").orElse("http://10.0.2.2:8000").get()),
        )
        buildConfigField(
            "String",
            "ATEP_VEHICLE_ID",
            quotedBuildConfig(providers.gradleProperty("ATEP_VEHICLE_ID").orElse("vehicle-001").get()),
        )
        buildConfigField(
            "String",
            "ATEP_MODULE_ID",
            quotedBuildConfig(providers.gradleProperty("ATEP_MODULE_ID").orElse("").get()),
        )
        buildConfigField(
            "String",
            "ATEP_MODULE_TOKEN",
            quotedBuildConfig(providers.gradleProperty("ATEP_MODULE_TOKEN").orElse("").get()),
        )
        buildConfigField(
            "String",
            "ATEP_OPERATOR_TOKEN",
            quotedBuildConfig(providers.gradleProperty("ATEP_OPERATOR_TOKEN").orElse("").get()),
        )
        buildConfigField(
            "String",
            "ATEP_TEST_RUN_ID",
            quotedBuildConfig(providers.gradleProperty("ATEP_TEST_RUN_ID").orElse("").get()),
        )
        buildConfigField(
            "String",
            "VEHICLE_PROPERTY_SOURCE",
            quotedBuildConfig(providers.gradleProperty("VEHICLE_PROPERTY_SOURCE").orElse("simulator").get()),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    signing
}

android {
    namespace = "com.vortexsoftware.android.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "VERSION_NAME", "\"${findProperty("VERSION_NAME")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Networking
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)

    // Image loading
    implementation(libs.coil.compose)

    // Google Sign-In for Google Contacts
    implementation(libs.play.services.auth)

    // QR Code generation
    implementation(libs.zxing.core)
}

// === Publishing Configuration ===

signing {
    useGpgCmd()
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.vortexsoftware.android", "vortex-sdk", findProperty("VERSION_NAME") as String)

    pom {
        name.set("Vortex Android SDK")
        description.set("Invitations-as-a-service SDK for Android. Dynamic invitation forms with email, SMS, social sharing, QR codes, and more.")
        url.set("https://github.com/teamvortexsoftware/vortex-android-sdk")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("vortexsoftware")
                name.set("Vortex Software")
                email.set("dev@vortexsoftware.com")
                organization.set("Vortex Software")
                organizationUrl.set("https://vortexsoftware.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/teamvortexsoftware/vortex-android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/teamvortexsoftware/vortex-android-sdk.git")
            url.set("https://github.com/teamvortexsoftware/vortex-android-sdk")
        }
    }
}

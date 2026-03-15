plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.chaquo.python")
    id("com.google.protobuf") version "0.9.6"
}

android {
    namespace = "com.example.applisante"
    compileSdk = 36

    androidResources {
        noCompress += "tflite"
    }

    defaultConfig {
        applicationId = "com.example.applisante"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
    buildFeatures {
        compose = true
        viewBinding = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc",
            )
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties",
        )
        jniLibs.pickFirsts += setOf(
            "**/libc++_shared.so",
            "**/libtensorflowlite_jni.so",
            "**/libtensorflowlite_flex_jni.so",
        )
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        pip {
            install("numpy")
            install("pandas")
            install("scipy")
            install("statsmodels")
        }
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.work.runtime.ktx)
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(project(":flower_tflite"))
    implementation("io.grpc:grpc-okhttp:1.79.0")
    implementation("io.grpc:grpc-stub:1.79.0")
    implementation("io.grpc:grpc-protobuf-lite:1.79.0")
    implementation("com.google.protobuf:protobuf-javalite:4.34.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("com.opencsv:opencsv:4.6")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.7"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.79.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
            task.plugins {
                create("grpc") { option("lite") }
            }
        }
    }
}

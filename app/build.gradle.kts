plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.aetheron.browser"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.aetheron.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.4.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


// Validate the bundled free-for-dev catalogue before every Android build.
tasks.register("validateFreeForDevCatalogue") {
    group = "verification"
    description = "Checks the bundled free-for-dev JSON catalogue for schema and data integrity."

    doLast {
        val catalogueFile = file("src/main/assets/free_for_dev_catalog.json")
        check(catalogueFile.isFile) {
            "Missing catalogue asset: ${catalogueFile.absolutePath}"
        }

        val root = groovy.json.JsonSlurper().parse(catalogueFile) as? Map<*, *>
            ?: error("Catalogue root must be a JSON object.")
        check(root["schemaVersion"] == 1) { "Unsupported catalogue schema version." }

        val categories = root["categories"] as? List<*>
            ?: error("Catalogue categories must be a JSON array.")
        val categoryIds = mutableSetOf<String>()
        var actualServiceCount = 0

        categories.forEachIndexed { categoryIndex, rawCategory ->
            val category = rawCategory as? Map<*, *>
                ?: error("Category at index $categoryIndex must be an object.")
            val categoryId = category["id"] as? String
            check(!categoryId.isNullOrBlank()) { "Category at index $categoryIndex has no id." }
            check(categoryIds.add(categoryId)) { "Duplicate category id: $categoryId" }
            check(!(category["name"] as? String).isNullOrBlank()) {
                "Category $categoryId has no name."
            }

            val services = category["services"] as? List<*>
                ?: error("Services for category $categoryId must be an array.")
            val serviceIds = mutableSetOf<String>()
            services.forEachIndexed { serviceIndex, rawService ->
                val service = rawService as? Map<*, *>
                    ?: error("Service $serviceIndex in $categoryId must be an object.")
                val serviceId = service["id"] as? String
                check(!serviceId.isNullOrBlank()) {
                    "Service $serviceIndex in $categoryId has no id."
                }
                check(serviceIds.add(serviceId)) {
                    "Duplicate service id $serviceId in category $categoryId."
                }
                check(!(service["name"] as? String).isNullOrBlank()) {
                    "Service $serviceId has no name."
                }
                check(!(service["description"] as? String).isNullOrBlank()) {
                    "Service $serviceId has no description."
                }
                val url = service["url"] as? String
                check(!url.isNullOrBlank() &&
                    (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("stun:"))
                ) { "Service $serviceId has an unsupported or missing URL." }
                actualServiceCount++
            }
        }

        check(root["categoryCount"] == categories.size) {
            "categoryCount metadata does not match the actual catalogue."
        }
        check(root["serviceCount"] == actualServiceCount) {
            "serviceCount metadata does not match the actual catalogue."
        }

        println(
            "Free-for-dev catalogue validation passed: ${categories.size} categories, " +
                "$actualServiceCount services."
        )
    }
}

tasks.named("preBuild").configure {
    dependsOn("validateFreeForDevCatalogue")
}

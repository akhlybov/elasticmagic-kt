kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                implementation(Libs.kotlinSerialization("json"))
            }
        }
    }
}
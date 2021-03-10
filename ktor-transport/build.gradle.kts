kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":elasticmagic-transport"))
                implementation(Libs.ktorClient("core"))
                implementation(Libs.ktorClient("encoding"))
                implementation(Libs.kotlinSerialization("json"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(Libs.ktorClient("mock"))
            }
        }
    }
}
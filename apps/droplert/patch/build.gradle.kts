plugins {
    id("app.reseam.patches")
}

dependencies {
    implementation(project(":apps:shared"))
    implementation(project(":apps:universal:patch"))
}

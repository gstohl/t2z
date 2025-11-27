rootProject.name = "zebrad-t2z-examples"

// Include the parent t2z library
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.zcash:t2z")).using(project(":"))
    }
}

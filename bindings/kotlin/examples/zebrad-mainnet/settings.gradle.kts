rootProject.name = "zebrad-testnet-demo"

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.zcash:t2z")).using(project(":"))
    }
}

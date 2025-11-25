extern crate napi_build;

use std::path::PathBuf;

fn main() {
    napi_build::setup();

    // Link to the pre-built t2z library
    let lib_dir = PathBuf::from("../rust/target/release");

    // Use the deps directory where the actual library files are
    let deps_dir = PathBuf::from("../rust/target/release/deps");

    println!("cargo:rustc-link-search=native={}", deps_dir.display());
    println!("cargo:rustc-link-search=native={}", lib_dir.display());

    // Force static linking by specifying static explicitly
    // and ensure the dylib is not preferred
    println!("cargo:rustc-link-lib=static=t2z");

    // Tell cargo to rerun if the library changes
    println!("cargo:rerun-if-changed=../rust/target/release/libt2z.a");
    println!("cargo:rerun-if-changed=../rust/target/release/deps/libt2z.a");
}

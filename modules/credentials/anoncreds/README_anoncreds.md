# ANONCREDS

The IOHK's anoncreds-rs repository is on [https://github.com/input-output-hk/anoncreds-rs](https://github.com/input-output-hk/anoncreds-rs)

See [anoncreds-rs repo](https://github.com/hyperledger/anoncreds-rs) & [ce-anoncreds-scala repo](https://github.com/input-output-hk/ce-anoncreds-scala).

See [ATL-4524 - [POLUX] Analyse usage in Scala](https://input-output.atlassian.net/browse/ATL-4524?focusedCommentId=173837)
https://github.com/hyperledger/anoncreds-rs/releases/tag/v0.1.0-dev.15

## ANONCREDS uniffi lib description

The file `anoncreds.udl` describes the warp interface generated with Uniffi.
The file can be found in the IOHK's anoncreds-rs repository [https://github.com/input-output-hk/anoncreds-rs/blob/main/uniffi/src/anoncreds.udl](https://github.com/input-output-hk/anoncreds-rs/blob/main/uniffi/src/anoncreds.udl)


## Build Jar lib

Build the NATIVE anoncred lib from IOHK's fork of the anoncreds-rs [https://github.com/input-output-hk/anoncreds-rs](https://github.com/input-output-hk/anoncreds-rs)

Go into the folder `uniffi` and generate the file `anoncreds.kt`

Assuming uniffi_bindgen is install. Install with `cargo install uniffi_bindgen --version $(cargo pkgid uniffi | cut -f 2 -d '@')`

Run the script `build-release-linux.sh` in there

Generate the Jar with `./gradlew jar` in the `output-frameworks/anoncreds-jvm` project

## Build the NATIVE lib 

Build the NATIVE anoncred lib from IOHK's fork of the anoncreds-rs [https://github.com/input-output-hk/anoncreds-rs](https://github.com/input-output-hk/anoncreds-rs)

Go into the folder `uniffi` and build the Native lib with:

- `cargo build --release --target x86_64-unknown-linux-gnu` (For Linux with x86_64)
- `cargo build --release --target x86_64-apple-darwin` (For Mac with x86_64)
- `cargo build --release --target aarch64-apple-darwin` (For Mac with arm)

## Copy files

Then copy the files
- from `target/x86_64-unknown-linux-gnu/release/libanoncreds_uniffi.so` to `pollux/lib/anoncreds/native-lib/NATIVE/linux/amd64/libuniffi_anoncreds.so`
- from `uniffi/output-frameworks/anoncreds-jvm/build/libs/anoncreds-jvm-1.0-SNAPSHOT.jar` to `pollux/lib/anoncreds//anoncreds-jvm-1.0-SNAPSHOT.jar`

```shell
rm -f pollux/lib/anoncreds/native-lib/NATIVE/linux/amd64/libuniffi_anoncreds.so
rm -f pollux/lib/anoncreds//anoncreds-jvm-1.0-SNAPSHOT.jar
cp ../anoncreds-rs/uniffi/target/x86_64-unknown-linux-gnu/release/libanoncreds_uniffi.so pollux/lib/anoncreds/native-lib/NATIVE/linux/amd64/libuniffi_anoncreds.so
cp ../anoncreds-rs/uniffi/output-frameworks/anoncreds-jvm/build/libs/anoncreds-jvm-1.0-SNAPSHOT.jar pollux/lib/anoncreds//anoncreds-jvm-1.0-SNAPSHOT.jar
```

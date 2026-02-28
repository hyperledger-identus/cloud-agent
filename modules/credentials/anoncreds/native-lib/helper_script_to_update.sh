#!/usr/bin/env bash

#  https://github.com/input-output-hk/anoncreds-rs/releases/download/0.4.0/library-darwin-aarch64-0ce097bbc9afa8a17a491248d75dcdf03f133dc4.tar.gz
RELEASES=https://github.com/input-output-hk/anoncreds-rs/releases/download/
VERSION="0.4.0"
SHA="0ce097bbc9afa8a17a491248d75dcdf03f133dc4"

rm -rf NATIVE/darwin-aarch64
rm -rf NATIVE/darwin-x86-64
rm -rf NATIVE/linux-aarch64
rm -rf NATIVE/linux-x86-64

mkdir NATIVE/darwin-aarch64
mkdir NATIVE/darwin-x86-64
mkdir NATIVE/linux-aarch64
mkdir NATIVE/linux-x86-64

wget -c $RELEASES/$VERSION/library-darwin-aarch64-$SHA.tar.gz -O - | tar -xz -C NATIVE/darwin-aarch64
wget -c $RELEASES/$VERSION/library-darwin-x86_64-$SHA.tar.gz -O - | tar -xz -C NATIVE/darwin-x86-64
wget -c $RELEASES/$VERSION/library-linux-aarch64-$SHA.tar.gz -O - | tar -xz -C NATIVE/linux-aarch64
wget -c $RELEASES/$VERSION/library-linux-x86_64-$SHA.tar.gz -O - | tar -xz -C NATIVE/linux-x86-64

rename  'libanoncreds_uniffi.so' 'libuniffi_anoncreds_wrapper.so' NATIVE/**/*.so
rename  'libanoncreds_uniffi.dylib' 'libuniffi_anoncreds_wrapper.dylib' NATIVE/**/*.dylib


## TODO missing anoncreds-jvm-1.0-SNAPSHOT.jar
## run https://github.com/input-output-hk/anoncreds-rs/blob/main/uniffi/build-release-linux.sh
## copy jar from uniffi/output-frameworks/anoncreds-jvm/build/libs/anoncreds-jvm-1.0-SNAPSHOT.jar

plugins {
    id("app.reseam.patches")
}

// To rebuild the native libs, run this from the repo root:
//
// On Windows or without make, swap
// -G "Unix Makefiles"
// for
// -G Ninja -DCMAKE_MAKE_PROGRAM=<path-to-ninja>
//
// NDK=${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/30.0.16248370}; SRC=apps/universal/extensions/signature-killer/src/main/cpp; OUT=$(mktemp -d); \
// for abi in armeabi-v7a arm64-v8a x86 x86_64; do cmake -S $SRC -B $OUT/$abi -G "Unix Makefiles" \
// -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$abi -DANDROID_PLATFORM=android-21 \
// -DCMAKE_BUILD_TYPE=Release && cmake --build $OUT/$abi && mkdir -p apps/universal/patch/src/main/resources/lib/$abi && \
// cp $OUT/$abi/libSignatureKiller.so apps/universal/patch/src/main/resources/lib/$abi/; done; rm -rf $OUT

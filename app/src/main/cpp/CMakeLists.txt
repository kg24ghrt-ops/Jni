cmake_minimum_required(VERSION 3.18.1)
project("paper_jni")

# 1. Import the pre-built Rust library
add_library(paper_engine SHARED IMPORTED)
set_target_properties(paper_engine PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaper_engine.so)

# 2. Build the JNI bridge
add_library(paper_jni SHARED paper_jni.cpp)

# 3. Link them together with Android and Log libraries
target_link_libraries(paper_jni paper_engine android log)
cmake_minimum_required(VERSION 3.28.3)
project("native-lib")

# Enable C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable optimizations
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O0 -g")

# For ARMv7, enable NEON explicitly
# For ARM64, NEON is enabled by default and -mfpu=neon is not supported
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
    add_definitions(-D__ARM_NEON__)
endif()

# For ARM64, NEON is always available
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_definitions(-D__ARM_NEON__)
endif()

# Enable SSE4.1 for x86
if(ANDROID_ABI STREQUAL "x86" OR ANDROID_ABI STREQUAL "x86_64")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -msse4.1")
    add_definitions(-D__SSE4_1__)
endif()

# Build the main native library from all C++ source files
add_library(native-lib SHARED 
    native-lib.cpp
    native-lib-simd.cpp
    ink_engine.cpp
    distort_engine.cpp
)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Link Android and required libraries
target_link_libraries(native-lib 
    android
    log
    jnigraphics
)

# Enable threading support
find_package(Threads REQUIRED)
target_link_libraries(native-lib Threads::Threads)

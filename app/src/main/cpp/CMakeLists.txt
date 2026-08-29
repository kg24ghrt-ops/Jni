cmake_minimum_required(VERSION 3.18.1)
project("native-lib")

# Build the native library from all C++ source files
add_library(native-lib SHARED 
    native-lib.cpp
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

cmake_minimum_required(VERSION 3.18.1)
project("homecil_native")

add_library(native-lib SHARED
        active-lib.cpp)   # or use the exact filename from your project

# Standard NDK libraries
find_library(android-lib android)
find_library(log-lib log)

target_link_libraries(native-lib
        ${android-lib}
        ${log-lib})
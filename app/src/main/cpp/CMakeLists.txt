cmake_minimum_required(VERSION 3.18.1)
project("homecil_native")

add_library(native-lib SHARED
        native-lib.cpp)

# Standard NDK libraries
find_library(android-lib android)
find_library(log-lib log)

target_link_libraries(native-lib
        ${android-lib}
        ${log-lib})
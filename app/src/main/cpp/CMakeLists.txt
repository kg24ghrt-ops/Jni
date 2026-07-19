cmake_minimum_required(VERSION 3.18.1)
project("homecil_native")

add_library(native-lib SHARED
        native-lib.cpp
        ink_engine.cpp)

find_library(android-lib android)
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(native-lib
        ${android-lib}
        ${log-lib}
        ${jnigraphics-lib})
cmake_minimum_required(VERSION 3.18.1)
project("homecil_native")

add_library(native-lib SHARED
        native-lib.cpp
        ink_engine.cpp
        distort_engine.cpp
        shader_loader.cpp
)

find_library(android-lib android)
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(vulkan-lib vulkan)

target_link_libraries(native-lib
        ${android-lib}
        ${log-lib}
        ${jnigraphics-lib}
        ${vulkan-lib}
)
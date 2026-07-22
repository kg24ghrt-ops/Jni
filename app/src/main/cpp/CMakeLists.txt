cmake_minimum_required(VERSION 3.18.1)
project("homecil_native")

# ------------------------------------------------------------------
# SHADER COMPILATION (OpenGL ES SPIR‑V)
# ------------------------------------------------------------------
set(SHADER_SRC_DIR ${CMAKE_SOURCE_DIR}/src/main/shaders)
set(SHADER_OUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/shaders)
set(SHADER_EMBED_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)

file(MAKE_DIRECTORY ${SHADER_OUT_DIR})
file(MAKE_DIRECTORY ${SHADER_EMBED_DIR})

# Find glslangValidator (NDK tool)
find_program(GLSLANG_VALIDATOR glslangValidator)
if(NOT GLSLANG_VALIDATOR)
    message(FATAL_ERROR "glslangValidator not found. Install NDK or set PATH.")
endif()

# Find xxd
find_program(XXD xxd)
if(NOT XXD)
    message(FATAL_ERROR "xxd not found. Install it.")
endif()

# Macro: compile .comp → .spv (OpenGL ES mode: -G)
macro(compile_shader_glsl INPUT_FILE OUTPUT_SPV)
    set(FULL_INPUT ${SHADER_SRC_DIR}/${INPUT_FILE})
    set(FULL_OUTPUT ${SHADER_OUT_DIR}/${OUTPUT_SPV})
    add_custom_command(
        OUTPUT ${FULL_OUTPUT}
        COMMAND ${GLSLANG_VALIDATOR} -G -o ${FULL_OUTPUT} ${FULL_INPUT}
        DEPENDS ${FULL_INPUT}
        COMMENT "Compiling ${INPUT_FILE} → ${OUTPUT_SPV} (OpenGL ES SPIR‑V)"
    )
    list(APPEND SHADER_SPV_FILES ${FULL_OUTPUT})
endmacro()

# Compile all shaders
compile_shader_glsl(capillary_map.comp capillary_map.spv)
compile_shader_glsl(ink_physics.comp ink_physics.spv)
compile_shader_glsl(ink_composite.comp ink_composite.spv)
compile_shader_glsl(paper_generation.comp paper_generation.spv)

# Macro: embed .spv → .h (C array)
macro(embed_spv INPUT_SPV OUTPUT_HEADER)
    set(FULL_INPUT ${SHADER_OUT_DIR}/${INPUT_SPV})
    set(FULL_OUTPUT ${SHADER_EMBED_DIR}/${OUTPUT_HEADER})
    add_custom_command(
        OUTPUT ${FULL_OUTPUT}
        COMMAND ${XXD} -i ${FULL_INPUT} > ${FULL_OUTPUT}
        DEPENDS ${FULL_INPUT}
        COMMENT "Embedding ${INPUT_SPV} → ${OUTPUT_HEADER}"
    )
    list(APPEND SHADER_HEADERS ${FULL_OUTPUT})
endmacro()

embed_spv(capillary_map.spv capillary_map.h)
embed_spv(ink_physics.spv ink_physics.h)
embed_spv(ink_composite.spv ink_composite.h)
embed_spv(paper_generation.spv paper_generation.h)

# ------------------------------------------------------------------
# NATIVE LIBRARY
# ------------------------------------------------------------------
add_library(native-lib SHARED
        native-lib.cpp
        ink_engine.cpp
        distort_engine.cpp
        shader_loader.cpp
)

# Include generated headers
target_include_directories(native-lib PRIVATE
        ${SHADER_EMBED_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link libraries
find_library(android-lib android)
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(EGL-lib EGL)
find_library(GLESv3-lib GLESv3)

target_link_libraries(native-lib
        ${android-lib}
        ${log-lib}
        ${jnigraphics-lib}
        ${EGL-lib}
        ${GLESv3-lib}
)

# Build shaders before compiling native library
add_dependencies(native-lib ${SHADER_HEADERS})
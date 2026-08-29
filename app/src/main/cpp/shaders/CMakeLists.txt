# This CMakeLists.txt is for reference only
# The shaders need to be pre-compiled to SPIR-V using glslc
# Example commands:
#   glslc -O paper.frag -o paper.frag.spv
#   glslc -O ink.comp -o ink.comp.spv
#   glslc -O distort.comp -o distort.comp.spv

# To compile all shaders:
#   for file in *.frag *.comp *.vert; do
#       glslc -O "$file" -o "${file%.glsl}.spv"
#   done

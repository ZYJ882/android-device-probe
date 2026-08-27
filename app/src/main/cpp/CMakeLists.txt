cmake_minimum_required(VERSION 3.22.1)
project(deviceprobe LANGUAGES C)

add_library(deviceprobe SHARED ProbeNative.c)

target_compile_features(deviceprobe PRIVATE c_std_11)
target_compile_options(deviceprobe PRIVATE -Wall -Wextra -Wformat=2)

target_link_libraries(deviceprobe android log)

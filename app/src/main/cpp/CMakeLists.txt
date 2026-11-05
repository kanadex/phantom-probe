cmake_minimum_required(VERSION 3.22.1)
project(phantom_probe CXX)

add_library(phantom_probe SHARED phantom_probe.cpp)

target_compile_options(phantom_probe PRIVATE -Os -fvisibility=hidden -fno-exceptions)
target_link_options(phantom_probe PRIVATE -Wl,--build-id=none)

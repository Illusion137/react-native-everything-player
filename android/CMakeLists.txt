project(EverythingPlayer)
cmake_minimum_required(VERSION 3.9.0)

set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_CXX_STANDARD 20)

# Create the shared library
add_library(EverythingPlayer SHARED)

# Include nitrogen-generated autolinking (adds sources, headers, and links)
include(${CMAKE_SOURCE_DIR}/../nitrogen/generated/android/EverythingPlayer+autolinking.cmake)

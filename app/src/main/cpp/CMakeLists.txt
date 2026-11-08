cmake_minimum_required(VERSION 3.22.1)

project(moonlight_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(moonlight_engine SHARED
        engine.cpp
        module_manager.cpp
        hook_registry.cpp
        jni_bridge.cpp)

target_include_directories(moonlight_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(moonlight_engine PRIVATE
        -Wall
        -Wextra
        -Wno-unused-parameter
        -fvisibility=hidden
        -fvisibility-inlines-hidden)

# Only the JNI entry points leave the library; everything else stays hidden.
target_compile_definitions(moonlight_engine PRIVATE MOONLIGHT_ENGINE_BUILD=1)

find_library(log-lib log)
target_link_libraries(moonlight_engine ${log-lib})

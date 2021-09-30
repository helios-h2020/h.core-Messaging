cmake_minimum_required(VERSION 3.4.1)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/nodejs-mobile/include/node/
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/node-addon-api/
)

add_library( libnode
    SHARED
    IMPORTED
)
set_target_properties( libnode
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/nodejs-mobile/bin/${ANDROID_ABI}/libnode.so
)

add_library( node_bridge
    SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/node_fd_pass.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/jni_node_bridge.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/stdio_redirect.cpp
)

target_compile_options( node_bridge
    PRIVATE
    $<$<OR:$<CXX_COMPILER_ID:Clang>,$<CXX_COMPILER_ID:AppleClang>,$<CXX_COMPILER_ID:GNU>>:
        -Wextra -Wall -Wconversion -Wsign-conversion -pedantic-errors>
    $<$<CXX_COMPILER_ID:MSVC>:
        /W4>
)

find_library(
    log-lib
    log
)

target_link_libraries(
    node_bridge
    libnode
    ${log-lib}
)

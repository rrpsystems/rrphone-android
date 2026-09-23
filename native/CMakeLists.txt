# libmsbcg729.so — o codec G.729 (bcg729 + filtro do mediastreamer2) numa
# biblioteca à parte, ligada às bibliotecas do SDK do liblinphone que já vão no
# APK. Compilado por build-g729.ps1; não faz parte do build do Gradle.
cmake_minimum_required(VERSION 3.22)
project(msbcg729 C)

set(LINPHONE_JNI_DIR "" CACHE PATH "Pasta jni/<abi> extraída do AAR do linphone-sdk-android")
if(NOT EXISTS "${LINPHONE_JNI_DIR}/libmediastreamer2.so")
    message(FATAL_ERROR "LINPHONE_JNI_DIR não contém libmediastreamer2.so: ${LINPHONE_JNI_DIR}")
endif()

set(SDK_INCLUDE "${CMAKE_CURRENT_SOURCE_DIR}/third_party/linphone-sdk-5.5.18")
set(BCG729_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/bcg729")

file(GLOB BCG729_SOURCES "${BCG729_DIR}/src/*.c")

add_library(msbcg729 SHARED
    msbcg729/plugin.c
    msbcg729/g729.c
    ${BCG729_SOURCES}
)

target_include_directories(msbcg729 PRIVATE
    "${BCG729_DIR}/include"
    "${BCG729_DIR}/src"
    "${SDK_INCLUDE}/bctoolbox"
    "${SDK_INCLUDE}/ortp"
    "${SDK_INCLUDE}/mediastreamer2"
)

# bcg729 exporta os símbolos com BCG729_VISIBILITY; aqui ele é estático dentro da .so.
target_compile_definitions(msbcg729 PRIVATE BCG729_STATIC)
target_compile_options(msbcg729 PRIVATE -O2 -fvisibility=hidden -Wno-unused-parameter)

foreach(lib mediastreamer2 ortp bctoolbox linphone)
    add_library(sdk_${lib} SHARED IMPORTED)
    set_target_properties(sdk_${lib} PROPERTIES IMPORTED_LOCATION "${LINPHONE_JNI_DIR}/lib${lib}.so")
    target_link_libraries(msbcg729 PRIVATE sdk_${lib})
endforeach()
target_link_libraries(msbcg729 PRIVATE log)

# Android 15+ exige alinhamento de 16 KB nas páginas das bibliotecas nativas.
target_link_options(msbcg729 PRIVATE "-Wl,-z,max-page-size=16384")

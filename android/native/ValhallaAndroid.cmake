include_guard(GLOBAL)

if(NOT ANDROID)
    message(FATAL_ERROR "ValhallaAndroid.cmake may only be used for Android builds")
endif()

set_property(
    GLOBAL
    PROPERTY VALHALLA_ANDROID_JNI_SOURCE
             "${CMAKE_CURRENT_LIST_DIR}/valhalla_jni.cpp"
)

function(valhalla_attach_android_jni)
    if(NOT TARGET valhalla-wrapper)
        message(FATAL_ERROR "valhalla-wrapper target does not exist")
    endif()

    get_property(
        android_jni_source
        GLOBAL
        PROPERTY VALHALLA_ANDROID_JNI_SOURCE
    )
    get_target_property(wrapper_sources valhalla-wrapper SOURCES)
    list(FIND wrapper_sources "${android_jni_source}" android_jni_index)
    if(NOT android_jni_index EQUAL -1)
        message(FATAL_ERROR "Android JNI source was already attached")
    endif()

    target_sources(valhalla-wrapper PRIVATE "${android_jni_source}")
endfunction()

# CMAKE_PROJECT_INCLUDE runs immediately after project(). Defer until the root
# directory finishes configuring so the shared wrapper target already exists.
cmake_language(
    DEFER
    DIRECTORY "${CMAKE_SOURCE_DIR}"
    CALL valhalla_attach_android_jni
)

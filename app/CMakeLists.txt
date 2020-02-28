cmake_minimum_required(VERSION 3.6)

find_library( log-lib
              log )

include_directories(src/main/cpp)

add_library( UDPSender
        SHARED
       src/main/cpp/UDPSender.cpp
        )

target_link_libraries( UDPSender
        ${log-lib}
        log
        android)
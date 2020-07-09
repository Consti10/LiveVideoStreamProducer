//
// Created by geier on 28/02/2020.
//

#include "UDPSender.h"
#include "Helper/NDKArrayHelper.hpp"
#include "Helper/AndroidLogger.hpp"

#include <jni.h>
#include <cstdlib>
#include <pthread.h>
#include <cerrno>
#include <sys/ioctl.h>
#include <endian.h>
#include <sys/socket.h>
#include <arpa/inet.h>

UDPSender::UDPSender(const std::string &IP,const int Port) {
    //create the socket
    sockfd = socket(AF_INET,SOCK_DGRAM,0);
    if (sockfd < 0) {
        MLOGD<<"Cannot create socket";
    }
    //Create the address
    address.sin_family = AF_INET;
    address.sin_port = htons(Port);
    inet_pton(AF_INET,IP.c_str(), &address.sin_addr);
}

//Split data into smaller packets when exceeding UDP max packet size
void UDPSender::send(const uint8_t *data, ssize_t data_length) {
    if(data_length<=0)return;
    if(data_length>UDP_PACKET_MAX_SIZE){
        const auto result=sendto(sockfd,data,UDP_PACKET_MAX_SIZE, 0, (struct sockaddr *)&(address), sizeof(struct sockaddr_in));
        if(result<0){
            MLOGE<<"Cannot send data "<<UDP_PACKET_MAX_SIZE<<" "<<strerror(errno);
        }else{
            //MLOGD<<"Sent "<<UDP_PACKET_MAX_SIZE;
        }
        send(&data[UDP_PACKET_MAX_SIZE],data_length-UDP_PACKET_MAX_SIZE);
    }else{
        const auto result=sendto(sockfd,data,(size_t)data_length, 0, (struct sockaddr *)&(address), sizeof(struct sockaddr_in));
        if(result<0){
            MLOGE<<"Cannot send data "<<data_length<<" "<<strerror(errno);
        }else{
            //MLOGD<<"Sent "<<data_length;
        }
    }
}



//----------------------------------------------------JAVA bindings---------------------------------------------------------------

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
      Java_constantin_testlivevideostreamproducer_UDPSender_##method_name

inline jlong jptr(UDPSender *p) {
    return reinterpret_cast<intptr_t>(p);
}
inline UDPSender *native(jlong ptr) {
    return reinterpret_cast<UDPSender*>(ptr);
}

extern "C" {

JNI_METHOD(jlong, nativeConstruct)
(JNIEnv *env, jobject obj, jstring ip,jint port) {
    return jptr(new UDPSender(NDKArrayHelper::DynamicSizeString(env,ip),(int)port));
}
JNI_METHOD(void, nativeDelete)
(JNIEnv *env, jobject obj, jlong p) {
    delete native(p);
}

JNI_METHOD(void, nativeSend)
(JNIEnv *env, jobject obj, jlong p,jobject buf,jint size) {
    //jlong size=env->GetDirectBufferCapacity(buf);
    auto *data = (jbyte*)env->GetDirectBufferAddress(buf);
    if(data== nullptr){
        MLOGE<<"Something wrong with the byte buffer (is it direct ?)";
    }
    //LOGD("size %d",size);
    native(p)->send((uint8_t*) data,(ssize_t) size);
}


}

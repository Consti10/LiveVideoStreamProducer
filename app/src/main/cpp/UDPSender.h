//
// Created by geier on 28/02/2020.
//

#ifndef LIVEVIDEOSTREAMPRODUCER_UDPSENDER_H
#define LIVEVIDEOSTREAMPRODUCER_UDPSENDER_H

#include <string>
#include <arpa/inet.h>

class UDPSender{
public:
    UDPSender(const std::string& IP,const int Port);
    void send(const uint8_t* data,ssize_t data_length);
private:
    int sockfd;
    sockaddr_in address;
    static constexpr const size_t UDP_PACKET_MAX_SIZE=65508-1;
};


#endif //LIVEVIDEOSTREAMPRODUCER_UDPSENDER_H

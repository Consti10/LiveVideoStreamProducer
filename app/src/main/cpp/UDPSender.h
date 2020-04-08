//
// Created by geier on 28/02/2020.
//

#ifndef LIVEVIDEOSTREAMPRODUCER_UDPSENDER_H
#define LIVEVIDEOSTREAMPRODUCER_UDPSENDER_H

#include <string>
#include <arpa/inet.h>

class UDPSender{
public:
    /**
     * Construct a UDP sender that sends UDP data packets
     * @param IP ipv4 address to send data to
     * @param Port port for sending data
     */
    UDPSender(const std::string& IP,const int Port);
    /**
     * send data to the ip and port set previously. Logs error on failure.
     * If data length exceeds the max UDP packet size, the method splits data and
     * calls itself recursively
     */
    void send(const uint8_t* data,ssize_t data_length);
private:
    int sockfd;
    sockaddr_in address;
    //https://en.wikipedia.org/wiki/User_Datagram_Protocol
    //65,507 bytes (65,535 − 8 byte UDP header − 20 byte IP header).
    static constexpr const size_t UDP_PACKET_MAX_SIZE=65507;
};


#endif //LIVEVIDEOSTREAMPRODUCER_UDPSENDER_H

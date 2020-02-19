package constantin.testlivevideostreamproducer;

//Put data in, hope the data comes out at the other end of the network

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class UDPSender {
    private static final String TAG="UDPSender";

    private final String IP; //"10.183.84.95"
    private static final int PORT=5600;
    private DatagramSocket udpSocket=null;
    private InetAddress address=null;
    private final boolean initialized;
    private static final int UDP_PACKET_MAX_SIZE=65508-1;
    //Use first byte as sequence number
    private static final int MAX_PACKET_SIZE=UDP_PACKET_MAX_SIZE-1;

    private final Context context;
    //private int nr=0;
    private byte seqNr=0;


    UDPSender(final Context context){
        this.context=context;
        IP= PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.KEY_SP_UDP_IP),"192.168.1.172");
        //IP="10.183.99.178";
        try {
            udpSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            Log.d(TAG,"Error cannot create DatagramSocket");
        }
        try {
            address = InetAddress.getByName(IP);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,"Error cannot create InetAddress "+IP);
        }
        initialized= udpSocket != null && address != null;
        if(initialized){
            Log.d(TAG,"Initialized UDPSender");
        }
    }

    //Send the UDP data on another thread,since networking is strictly forbidden on the UI thread
    public void sendAsync(final ByteBuffer data){
        if(!initialized){
            return;
        }
        final byte[] array = new byte[data.remaining()];
        data.get(array);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                sendUDPData(array,0,array.length);
            }
        });
    }

    //When using a special Handler for the Encoder*s callbacks
    //We do not need to create an extra thread
    public void sendOnCurrentThread(final ByteBuffer data){
        if(!initialized){
            return;
        }
        final byte[] array = new byte[data.remaining()];
        data.get(array);
        sendUDPData(array,0,array.length);
    }

    //Called by sendAsync / sendOnCurrentThread
    //If length exceeds the max UDP packet size,
    //The data is split into smaller chunks and the method calls itself recursively
    private void sendUDPData(final byte[] data,int offset,int length){
        if(length<=0)return;
        if(length>UDP_PACKET_MAX_SIZE){
            sendUDPPacket(data,offset,UDP_PACKET_MAX_SIZE);
            //Log.d(TAG,"Split msg");
            sendUDPData(data,offset+UDP_PACKET_MAX_SIZE,length-UDP_PACKET_MAX_SIZE);
        }else{
            sendUDPPacket(data,offset,length);
        }
    }

    //buff has to be max size==MAX_UDP_PACKET_SIZE
    private void sendUDPPacket(final byte[] packetData,int offset,int length){
        //write the sequence number:
        /*byte[] data=new byte[length+1];
        writeSeqNr(data);
        System.arraycopy(packetData,offset,data,1,length);
        DatagramPacket packet = new DatagramPacket(data,0,data.length,address,PORT);*/

        DatagramPacket packet = new DatagramPacket(packetData,offset,length,address,PORT);
        if(length>UDP_PACKET_MAX_SIZE){
            Log.d(TAG,"Error packet too big");
        }
        try {
            udpSocket.send(packet);
            //Log.d(TAG,"Send data"+packet.getLength());
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG,"Cannot send packet");
        }
    }

    private void writeSeqNr(byte[] data){
        /*byte[] bytes = ByteBuffer.allocate(4).putInt(nr).array();
        for(int i=0;i<4;i++){
            data[i]=bytes[i];
        }*/
        data[0]=seqNr;
        seqNr++;
    }

}

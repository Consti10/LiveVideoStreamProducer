package constantin.testlivevideostreamproducer;

//Put data in, hope the data comes out at the other end of the network

import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class UDPSender {
    private static final String TAG="UDPSender";

    private static final String IP="192.168.1.172"; //"10.183.84.95"
    private static final int PORT=5600;
    private DatagramSocket udpSocket=null;
    private InetAddress address=null;
    private final boolean initialized;


    UDPSender(){
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
            Log.d(TAG,"Error cannot create InetAddress");
        }
        initialized= udpSocket != null && address != null;
        if(initialized){
            Log.d(TAG,"Initialized UDPSender");
        }
    }

    //Send the UDP data on another thread,since networking is strictly forbidden on the UI thread
    public void sendAsync(ByteBuffer data){
        if(!initialized){
            return;
        }
        final byte[] buff = new byte[data.remaining()];
        data.get(buff);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                sendData(buff);
            }
        });
    }

    //When using a special Handler for the Encoder*s callbacks
    //We do not need to create an extra thread
    public void sendOnCurrentThread(ByteBuffer data){
        if(!initialized){
            return;
        }
        final byte[] buff = new byte[data.remaining()];
        data.get(buff);
        sendData(buff);
    }

    //Called by sendAsync / sendOnCurrentThread
    private void sendData(final byte[] buff){
        DatagramPacket packet = new DatagramPacket(buff,buff.length, address,PORT);
        try {
            udpSocket.send(packet);
            //Log.d(TAG,"Send data"+packet.getLength());
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG,"Cannot sendAsync packet");
        }
    }

}

package constantin.testlivevideostreamproducer;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class IPResolver {
    //Try to find the IP of the ground unit - e.g. the first device connected to the WIFI hotspot
    //This methods makes some assumptions that might not be true on all devices - but testing is the only
    //way to find out if they work
    //return IP on success, null otherwise
    public static String resolveIpConnectedToHotspot(final Context c){
        final ArrayList<String> ipsToTest=getIpsToPing();

       final List<String> allReachableAddresses=pingAllIPsMultiThreaded(ipsToTest,32);
        for(final String ip:allReachableAddresses){
            System.out.println("Found ip:"+ip);
        }
        if(allReachableAddresses.size()==0){
            return null;
        }
        return allReachableAddresses.get(0);
    }

    /**
     * split work into chunks and reduce in the end.
     * Since the ping is implemented as a blocking call, creating more threads
     * than logical cpu cores still provides a speedup. However, creating a thread is a
     * expensive operation itself, so increasing N_THREADS will not always reduce execution time
     **/
    private static List<String> pingAllIPsMultiThreaded(final ArrayList<String> ipsToTest, final int N_THREADS){
        final long startTime=System.currentTimeMillis();
        final ArrayList<String > allReachableIPs=new ArrayList<>();
        final ArrayList<Thread> workers=new ArrayList<>();
        final ArrayList<List<String>> chunks=splitIntoChunks(ipsToTest,N_THREADS);
        for(int i=0;i<N_THREADS;i++){
            final List<String> chunk=chunks.get(i);
            final Runnable runnable=new Runnable() {
                @Override
                public void run() {
                    final List<String > reachableIPs=pingAllIPs(chunk,200);
                    synchronized (allReachableIPs){
                        allReachableIPs.addAll(reachableIPs);
                    }
                }
            };
            final Thread worker=new Thread(runnable);
            worker.start();
            workers.add(worker);
        }
        for(final Thread worker:workers){
            try {
                worker.join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
                worker.interrupt();
                Thread.currentThread().interrupt();
            }
        }
        final long delta=System.currentTimeMillis()-startTime;
        System.out.println("Pinging all ips took ms:"+delta+allReachableIPs.toString());
        return allReachableIPs;
    }

    /**
     * blocks until either all ips have been tested or when the calling thread is interrupted
     * When the calling thread is interrupted, returns after max time PING_TIMEOUT_MS
     * Complexity is O(n), time is roughly ipsToPing.size * PING_TIMEOUT_MS
     **/
    private static List<String> pingAllIPs(final List<String> ipsToPing,final int PING_TIMEOUT_MS){
        final ArrayList<String> reachableIPs=new ArrayList<>();
        for(final String ip:ipsToPing){
            if(Thread.currentThread().isInterrupted()){
                return reachableIPs;
            }
            try {
                final InetAddress address=InetAddress.getByName(ip);
                final boolean reachable=address.isReachable(PING_TIMEOUT_MS);
                if(reachable){
                    //IP found !
                    reachableIPs.add(ip);
                    //System.out.println("Reached "+address.toString());
                }else{
                    //System.out.println("Cannot reach "+address.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return reachableIPs;
    }

    /**
     * Takes a generic array and splits it into nChunks sub-arrays
     * Sub-arrays are not required to have same length.
     */
    private static <T> ArrayList<List<T>> splitIntoChunks(final ArrayList<T> data,final int nChunks){
        ArrayList<List<T>> ret=new ArrayList<>();
        //if data.size() is not a multiple of nChunks, the last chunk will be smaller
        final int chunkSize=(int) Math.ceil((double)data.size() / nChunks);
        //System.out.println(data.size()+" "+nChunks+" "+chunkSize);
        for(int i=0;i<nChunks;i++){
            int end=(i+1)*chunkSize;
            if(end>data.size())end=data.size();
            final List<T> chunk=data.subList(i*chunkSize,end);
            ret.add(chunk);
        }
        return ret;
    }

    //Returns the ip address of the network interface that is most likely the hotspot providing interface
    //Returns null on failure
    private static String getIpOfHotspotProvider(){
        final ArrayList<Inet4Address> addresses=new ArrayList<>();
        try{
            final Enumeration<NetworkInterface> networkInterfacesEnumeration=NetworkInterface.getNetworkInterfaces();
            while (networkInterfacesEnumeration.hasMoreElements()){
                final NetworkInterface networkInterface=networkInterfacesEnumeration.nextElement();
                if(!networkInterface.isUp() || networkInterface.getName().contains("dummy0") || networkInterface.isLoopback()){
                    continue;
                }
                //System.out.println("network if"+networkInterface.getName());
                if(networkInterface.getName().contains("wlan")){
                    final Enumeration<InetAddress> inetAddressesEnumeration=networkInterface.getInetAddresses();
                    while (inetAddressesEnumeration.hasMoreElements()){
                        InetAddress inetAddress=inetAddressesEnumeration.nextElement();
                        //System.out.println("address"+inetAddress.getHostAddress());
                        if(inetAddress instanceof Inet4Address){
                            addresses.add((Inet4Address)inetAddress);
                        }
                    }
                }
            }
            if(addresses.size()>0){
                return addresses.get(addresses.size()-1).getHostAddress();
            }else{
                return "";
            }
        }catch(Exception e){e.printStackTrace();}
        return "";
    }

    private static ArrayList<String> getIpsToPing(){
        final String hotspotIp=getIpOfHotspotProvider();
        System.out.println("Hotspot ip is"+hotspotIp);
        if(hotspotIp==null){
            return createDefaultIPs();
        }
        //Assume that the connected device has the same first 3 digits as the hotspot
        final int[] elements=stringToIp(hotspotIp);
        if(elements==null){
            return createDefaultIPs();
        }
        final ArrayList<String> ret=new ArrayList<>();
        for(int i=0;i<256;i++){
            if(i!=elements[3]){
                final String s=elements[0]+"."+elements[1]+"."+elements[2]+"."+i;
                ret.add(s);
            }
        }
        return ret;

    }

    //We might have luck with the 192.168.43 prefix
    //but most likely not !
    private static ArrayList<String> createDefaultIPs(){
        final ArrayList<String> ret=new ArrayList<>();
        for(int i=2;i<256;i++){
            final String s="192.168.43."+i;
            ret.add(s);
        }
        return ret;
    }


    //Returns the 4 digits that make up an ip.
    //Example: 192.168.1.1 -> [192,168,1,1]
    //returns null on failure
    private static int[] stringToIp(final String ip){
        //System.out.println("Ip is "+ip);
        String[] sub = ip.split("\\.");
        //for(int i=0;i<sub.length;i++){
        //    System.out.println("Chunck "+sub[i]);
        //}
        if(sub.length!=4)return null;
        final int[] ret=new int[4];
        for(int i=0;i<4;i++){
            try{
                ret[i]=Integer.parseInt(sub[i]);
            }catch (NumberFormatException e){
                e.printStackTrace();
                return null;
            }
        }
        return ret;
    }

}

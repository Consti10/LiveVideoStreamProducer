package constantin.testlivevideostreamproducer;

import android.content.Context;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class IPResolver {

    //Assume that all devices connected to the hotspot start
    //with 192.168.1
    //return IP on success, null otherwise
    public static String resolveIpConnectedToHotspot(final Context c){
        final ArrayList<String> ipsToTest=new ArrayList<>();
        for(int i=2;i<256;i++){
            //final String s="192.168.43."+i;
            final String s="10.183.99."+i;
            ipsToTest.add(s);
        }
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
     * Than logical cpu cores still provides a speedup. However, creating a thread is a
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

}

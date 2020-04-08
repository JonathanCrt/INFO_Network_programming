package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;

public class ClientLongSum {
	
	public static final Logger logger = Logger.getLogger(ClientLongSum.class.getName());
    private static final int BUFFER_SIZE = 1024;
    

    private static ArrayList<Long> randomLongList(int size){
        Random rng = new Random();
        ArrayList<Long> list = new ArrayList<>(size);
        for(int i = 0; i < size; i++){
            list.add(rng.nextLong());
        }
        return list;
    }

    private static boolean checkSum(List<Long> list, long response) {
        long sum = 0;
        for(long l : list)
            sum += l;
        return sum==response;
    }


    /**
     * Write all the longs in list in BigEndian on the server
     * and read the long sent by the server and returns it
     *
     * returns Optional.empty if the protocol is not followed by the server but no IOException is thrown
     *
     * @param sc
     * @param list
     * @return
     * @throws IOException
     */
    private static Optional<Long> requestSumForList(SocketChannel sc, List<Long> list) throws IOException {
       
    	var buffer = ByteBuffer.allocate(BUFFER_SIZE);
    	var numberOfLongBytes = Long.BYTES;
    	var nbOperands = list.size();
    	buffer.putInt(nbOperands); // on put la size (int) dans le buffer pour la requête
    	for(var elt : list) {
    	   if(numberOfLongBytes > buffer.remaining()) { // Si le nombre de long (bytes) à la zone de travail
    		   buffer.flip(); // toujours avant d'écrire dans socketChannel
    		   sc.write(buffer); // on écrit le buffer dans le sc
    		   buffer.clear(); // puis on nettoie le buffer
    	   }
    	   buffer.putLong(elt);
    	}
    	buffer.flip(); // toujours avant d'écrire dans socketChannel
    	sc.write(buffer);
       
    	buffer.clear(); // on nettoie le buffer
    	var sumOfOperandsIntoBuffer = buffer.limit(numberOfLongBytes);
    	if(!readFully(sc, sumOfOperandsIntoBuffer)) {
    		return Optional.empty();
    	}
    
    	sumOfOperandsIntoBuffer.flip(); // Toujours avant le getter sur buff
    	return Optional.ofNullable(sumOfOperandsIntoBuffer.getLong());
    }

    
    /**
	  * Fill the workspace of the Bytebuffer with bytes read from sc.
	  *
	  * @param sc
	  * @param bb
	  * @return false if read returned -1 at some point and true otherwise
	  * @throws IOException
	  */
 	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
 		while (bb.hasRemaining()) {
 			if (sc.read(bb) == -1) {
 				logger.warning("[readFully] read() return -1 ! ");
 				return false;
 			}	
 		}
		return true;
 	}
    
    
    
    public static void main(String[] args) throws IOException {
        InetSocketAddress server = new InetSocketAddress(args[0],Integer.valueOf(args[1]));
        try (SocketChannel sc = SocketChannel.open(server)) {
            for(int i=0; i<5; i++) {
                ArrayList<Long> list = randomLongList(50);

                Optional<Long> l = requestSumForList(sc, list);
                if (!l.isPresent()) {
                    System.err.println("Connection with server lost.");
                    return;
                }
                if (!checkSum(list, l.get())) {
                    System.err.println("Oups! Something wrong happens!");
                }
            }
            System.err.println("Everything seems ok");
        }
    }
}

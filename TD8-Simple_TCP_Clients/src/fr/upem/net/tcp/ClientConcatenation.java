package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientConcatenation {
	
	public static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private static final Charset UTF8 = Charset.forName("UTF8");
	
	
	
	private static Optional<String> requestStringForConcatenation (SocketChannel sc, List<String> list) throws IOException {
		
		var senderbuffer = ByteBuffer.allocate(BUFFER_SIZE);
		
    	var numberOfIntBytes = Integer.BYTES;
    	var nbStrings = list.size();
    	
    	senderbuffer.putInt(nbStrings); // on met le nombre de chaînes
    	for(var st: list) { 
    		
    		var encodedBufferStrings = UTF8.encode(st);
    		var content = numberOfIntBytes + encodedBufferStrings.capacity();
    		if (content > senderbuffer.remaining()) {
    			senderbuffer.flip(); // Toujours av write sur sc
				sc.write(senderbuffer);
				senderbuffer.clear(); // on nettoie le buffer
			}
    		senderbuffer.putInt(encodedBufferStrings.capacity()); // On put la toute la capacité du senderBuffer + capacity -> remaining
    		senderbuffer.put(encodedBufferStrings);  // on put les données (encodées)
    	}
    	// put -> write mode 
    	
    	senderbuffer.flip(); // Toujours av write sur sc
    	sc.write(senderbuffer);
		
    	
    	senderbuffer.clear(); // on nettoie le buffer
    	var allStringsIntoBuffer = senderbuffer.limit(numberOfIntBytes);
    	if (!readFully(sc, allStringsIntoBuffer)) { // call helper method
    		return Optional.empty();
    	}
    	
    	senderbuffer.flip(); // Toujours av le getter
    	var receivedBuffer = ByteBuffer.allocate(senderbuffer.getInt());
		if (!readFully(sc, receivedBuffer)) { // call helper method
			return Optional.empty();
		}
		
		receivedBuffer.flip(); // tjrs avant lecture pour decode
		var decodedBufferString  = UTF8.decode(receivedBuffer).toString(); // decode la réponse dans le buffer de réception
		return Optional.ofNullable(decodedBufferString); // Retourne la réponse
 
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
		InetSocketAddress concatenationServer = new InetSocketAddress(args[0],Integer.valueOf(args[1]));
		try (var scanner = new Scanner(System.in); SocketChannel sc = SocketChannel.open(concatenationServer)) {
			var type = "";
			var listOfStrings = new ArrayList<String>();
			
			while ((!(type = scanner.next()).equals("end") 
					&& scanner.hasNext())) { // Tant qu'on saisie "end" et qu'il a une string
				listOfStrings.add(type);
			}
				
			var res = requestStringForConcatenation(sc, listOfStrings);
			
			if (!res.isEmpty()) {
				logger.info(res.get());
			} else {
				logger.warning("Connection with server lost.");
            	return;
			}
				
		}
	}
}

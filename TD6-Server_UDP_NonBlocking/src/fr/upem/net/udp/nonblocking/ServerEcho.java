package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEcho {

    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());

    private final DatagramChannel dc; 
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE); // pour les réception
    private SocketAddress exp; // expediteur , connaitre la client qui nous a contacté
    private int port;

    public ServerEcho(int port) throws IOException {
        this.port=port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ); // On attend la réception de paquets
 		  // TODO set dc in non-blocking mode and register it to the selector
   }

    /**
     * Bloque jusqu'a l'arrivée d'un paquet
     * Dés qu'un paquet arrive le selecteur va rempli l'ensemble Selected Keys
     * avec la clé de notre selecteur -> Appel de la méthode tratKey
     * @throws IOException
     */
    public void serve() throws IOException {
        logger.info("ServerEcho started on port "+port);
        try { // on rattrapage l'uncheck exception à la sortir de la boucle et on la retransforme en une IOException
        	while (!Thread.interrupted()) {
                selector.select(this::treatKey);
            }
        } catch (UncheckedIOException e) {
        	throw e.getCause();
        }
        
    }

    private void treatKey(SelectionKey key) {
        try{
	        if (key.isValid() && key.isWritable()) {
	            doWrite(key);
	        }
	        if (key.isValid() && key.isReadable()) {
	            doRead(key);
	        }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // on encapulse à l'intérieur la cause l'exception qui aurai été levé par doRead()  et doWrite()
        }

    }

    
    private void doRead(SelectionKey key) throws IOException {
    	buff.clear(); // toujours av le receive
    	exp = dc.receive(buff);
    	if(exp == null) { // Si le selecteur s'est trompé et si  l'adresse renvoyée par receive est null
    		return; // La réception n'a pas marché
    	}
    	buff.flip(); // flip à la réception 
    	key.interestOps(SelectionKey.OP_WRITE); // je soihaite être notifié quand un envoi est possible


	 }

    private void doWrite(SelectionKey key) throws IOException {
   		dc.send(buff, exp);
   		if(buff.hasRemaining()) { // Si il reste des données dans le buffer (l'envoi ne s'est pas passé)
   			return; // On attend que le selecteur nous repropose une clé, nous re-propose d'envoyer
   		}
   		key.interestOps(SelectionKey.OP_READ);
	}

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerEcho server= new ServerEcho(Integer.valueOf(args[0]));
        server.serve();
    }




}

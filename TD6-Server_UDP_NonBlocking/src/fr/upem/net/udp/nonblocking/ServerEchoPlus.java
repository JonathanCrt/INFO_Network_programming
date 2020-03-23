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

public class ServerEchoPlus {
	private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());

	private final DatagramChannel dc;
	private final Selector selector;
	private final int BUFFER_SIZE = 1024;
	private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE); // pour les réception
	private SocketAddress exp; // expediteur , connaitre la client qui nous a contacté
	private int port;

	public ServerEchoPlus(int port) throws IOException {
		this.port = port;
		selector = Selector.open();
		dc = DatagramChannel.open();
		dc.bind(new InetSocketAddress(port));
		dc.configureBlocking(false);
		dc.register(selector, SelectionKey.OP_READ); // On attend la réception de paquets

	}

	/**
	 * Bloque jusqu'a l'arrivée d'un paquet Dés qu'un paquet arrive le selecteur va
	 * rempli l'ensemble Selected Keys avec la clé de notre selecteur -> Appel de la
	 * méthode tratKey
	 * 
	 * @throws IOException
	 */
	public void serve() throws IOException {
		logger.info("ServerEchoPlus started on port " + port);
		try {
			while (!Thread.interrupted()) {
				selector.select(this::treatKey);
			}
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}

	}

	private void treatKey(SelectionKey key) {
		try {
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}

	private void doRead(SelectionKey key) throws IOException {
		buff.clear(); // toujours av le receive
		exp = dc.receive(buff);
		if (exp == null) {
			return;
		}
		buff.flip();
		var i = buff.position();
		while (i < buff.limit()) {
			var incementedData = ((buff.get(i) + 1) % 255);
			buff.put((byte) incementedData); // On put dans le buffer des données incrémentées 1 modulo 255
			i++;
		}
		buff.flip();
		key.interestOps(SelectionKey.OP_WRITE);

	}

	private void doWrite(SelectionKey key) throws IOException {
		dc.send(buff, exp);
		if (buff.hasRemaining()) {
			return;
		}
		key.interestOps(SelectionKey.OP_READ);
	}

	public static void usage() {
		System.out.println("Usage : ServerEchoPlus port");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		ServerEchoPlus server = new ServerEchoPlus(Integer.valueOf(args[0]));
		server.serve();
	}
}

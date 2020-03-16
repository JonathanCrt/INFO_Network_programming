package fr.upem.net.udp;

import java.util.logging.Logger;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;

public class ServerIdUpperCaseUDP {

	private static final Logger logger = Logger.getLogger(ServerIdUpperCaseUDP.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final DatagramChannel dc;
	private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private static final Charset UTF8 = Charset.forName("UTF8");

	public ServerIdUpperCaseUDP(int port) throws IOException {
		dc = DatagramChannel.open();
		dc.bind(new InetSocketAddress(port));
		logger.info("ServerBetterUpperCaseUDP started on port " + port);
	}

	/**
	 * Place le serveur en attente de réception de requêtes de clients
	 * 
	 * @throws IOException
	 */
	public void serve() throws IOException {
		while (!Thread.interrupted()) {

			this.buff.clear();
			var exp = (InetSocketAddress) dc.receive(this.buff); // 1) receive request from client
			this.buff.flip();

			var id = this.buff.getLong(); // 2) read id
			var decodedMsg = UTF8.decode(this.buff).toString(); // 3) decode msg in request
			var upperCaseMsg = decodedMsg.toUpperCase();

			this.buff.clear();
			this.buff.putLong(id);
			this.buff.put(UTF8.encode(upperCaseMsg)); // 4) create packet with id, upperCaseMsg in UTF-8

			logger.info(upperCaseMsg);
			this.dc.send(this.buff.flip(), exp); // 5) send the packet to client

		}
		dc.close();
	}

	public static void usage() {
		System.out.println("Usage : ServerIdUpperCaseUDP port");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		ServerIdUpperCaseUDP server;
		int port = Integer.valueOf(args[0]);
		if (!(port >= 1024) & port <= 65535) {
			logger.severe("The port number must be between 1024 and 65535");
			return;
		}
		try {
			server = new ServerIdUpperCaseUDP(port);
		} catch (BindException e) {
			logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
			return;
		}
		server.serve();
	}
}

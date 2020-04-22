package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * class which represent an iterative server (no efficient)
 * 
 * @author jonat 
 * Execution note : servor is serving first client Second client
 *         say "Connection accepted by client" : pending connection ,
 *         pre-accepted by system(OS)
 */
public class IterativeLongSumServer {

	private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private static final int INT_SIZE = Integer.BYTES;
	private static final int LONG_SIZE = Long.BYTES;

	public IterativeLongSumServer(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName() + " starts on port " + port);
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 */

	public void launch() throws IOException {
		logger.info("Server started");
		while (!Thread.interrupted()) {
			SocketChannel client = serverSocketChannel.accept();
			try {
				logger.info("Connection accepted from " + client.getRemoteAddress());
				serve(client);
			} catch (IOException ioe) {
				logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
			} catch (InterruptedException ie) {
				logger.info("Server interrupted");
				break;
			} finally {
				silentlyClose(client); // to accept a new client
			}
		}
	}

	/**
	 * Treat the connection sc applying the protocole All IOException are thrown
	 *
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void serve(SocketChannel sc) throws IOException, InterruptedException {

		var bb = ByteBuffer.allocate(BUFFER_SIZE);

		for (;;) {
			var sumOperands = 0L;
			bb.clear(); // we clean buffer
			var bbLimitInt = bb.limit(INT_SIZE);
			if (!readFully(sc, bbLimitInt)) { // if we overflow size of int in bytes
				return; // we leave method
			}
			bb.flip(); // read-mode
			var nbOperands = bb.getInt(); // Reads the next four bytes at this buffer's current position,composing them
											// into an int value

			var i = 0;
			while (i < nbOperands) {
				bb.clear(); // write-mode after
				var bbLimitLong = bb.limit(LONG_SIZE);
				if (!readFully(sc, bbLimitLong)) { // if we overflow size of long in bytes
					return;
				}
				bb.flip(); // because getLong wait read-mode
				sumOperands += bb.getLong(); // calculate sum of operands
				i++;
			}
			bb.clear(); // write-mode after
			bb.putLong(sumOperands);
			bb.flip(); // because write(..) wait read-mode
			sc.write(bb);

		}

	}

	/**
	 * Treat the connection sc applying the protocole All IOException are thrown
	 * Warning : we allocate unbounded ressources !
	 * 
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void serveCorrection(SocketChannel sc) throws IOException, InterruptedException {
		// we know we have 4 octets + 8 octets = number of longs + operands
		ByteBuffer bbInt = ByteBuffer.allocate(INT_SIZE);
		ByteBuffer bbLong = ByteBuffer.allocate(LONG_SIZE);
		while (!Thread.interrupted()) {
			bbInt.clear(); // read-mode --> write-mode
			if (!readFully(sc, bbInt)) {
				logger.info("Conenction closed by client");
				return;
			}
			var nbLongs = bbInt.flip().getInt();
			if (nbLongs <= 0) { // we check number of longs is positive number (check if protocol is ok)
				logger.info("The client send a wrong number of longs : " + nbLongs);
				return;
			}
			ByteBuffer buff = ByteBuffer.allocate(nbLongs * LONG_SIZE); // allocate a buffer with the rest of the
																		// request : no realist !

			if (!readFully(sc, buff)) { // read until filled buffer
				logger.info("Connection closed by client before sending the longs");
				return;
			}
			buff.flip();
			var sumOperands = 0l;
			while (buff.hasRemaining()) {
				sumOperands += buff.getLong(); // calculate sum of operands
			}
			bbLong.clear(); // write-mode after
			bbLong.putLong(sumOperands);
			bbLong.flip(); // because write(..) wait read-mode
			sc.write(bbLong);

		}
	}

	/**
	 * Treat the connection sc applying the protocole All IOException are thrown
	 * Version 2 with ensure method, allocate fixed size of buffer
	 * 
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void serveCorrection2(SocketChannel sc) throws IOException, InterruptedException {
		ByteBuffer bbIn = ByteBuffer.allocate(BUFFER_SIZE).flip(); // bbIn always in read-mode, received buffer (all
																	// read into)
		ByteBuffer bbOut = ByteBuffer.allocate(LONG_SIZE);

		while (!Thread.interrupted()) {
			if (!ensure(sc, bbIn, INT_SIZE)) {
				logger.info("Connection closed by client");
				return;
			}
			var sumOperands = 0l;
			var nbLongs = bbIn.getInt();
			if (nbLongs <= 0) {
				logger.info("The client send a wrong number of longs : " + nbLongs);
				return;
			}
			while (nbLongs != 0 && ensure(sc, bbIn, LONG_SIZE)) {
				sumOperands += bbIn.getLong();
				nbLongs--;
			}
			if (nbLongs != 0) {
				return;
			}
			bbOut.clear(); // write-mode after
			bbOut.putLong(sumOperands);
			bbOut.flip(); // because write(..) wait read-mode
			sc.write(bbOut);

		}
	}

	/**
	 * add data to working area to guarantee a certain size of it can read more than
	 * 4 bytes, but guarantee we have at least 4 bytes !
	 * 
	 * @param sc
	 * @param bb
	 * @param size
	 * @return
	 * @throws IOException
	 */
	private static boolean ensure(SocketChannel sc, ByteBuffer bb, int size) throws IOException {
		assert (size <= bb.capacity()); // check if size is not greater than size of buffer
		while (bb.remaining() < size) { // while work area is less than size
			bb.compact(); // read --> write mode
			try {
				if (sc.read(bb) == -1) {
					logger.info("Input stream closed");
					return false;
				}
			} finally {
				bb.flip(); // write --> read-mode
			}
		}
		return true;
	}

	/**
	 * Close a SocketChannel while ignoring IOExecption
	 *
	 * @param sc
	 */

	private void silentlyClose(SocketChannel sc) {
		if (sc != null) {
			try {
				sc.close();
			} catch (IOException e) {
				// Do nothing
			}
		}
	}

	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		while (bb.hasRemaining()) {
			if (sc.read(bb) == -1) {
				logger.info("Input stream closed");
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		IterativeLongSumServer server = new IterativeLongSumServer(Integer.parseInt(args[0]));
		server.launch();
	}
}

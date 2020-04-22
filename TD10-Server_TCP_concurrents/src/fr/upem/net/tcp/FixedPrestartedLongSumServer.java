package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServer {

	private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private static final int INT_SIZE = Integer.BYTES;
	private static final int LONG_SIZE = Long.BYTES;
	private final int maxClients;

	public FixedPrestartedLongSumServer(int port, int numberPermits) throws IOException {

		this.maxClients = numberPermits;
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName() + " starts on port " + port);
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */

	public void launch() throws IOException {
		logger.info("Server started");
		for (int i = 0; i < this.maxClients; i++) {
			new Thread(() -> {
				try {

					while (!Thread.interrupted()) {

						SocketChannel client = serverSocketChannel.accept();
						try {
							logger.info("Connection accepted from " + client.getRemoteAddress());
							serve(client);
						} catch (IOException ioe) {
							logger.info("Connection terminated with client by IOException" + ioe.getCause());
						} catch (InterruptedException ie) {
							logger.info("Server interrupted");
							return;
						} finally {
							silentlyClose(client);
						}
					}

				} catch (AsynchronousCloseException e) {
					logger.severe("Worker thread was stopped");
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Worker thread was stopped", e.getCause());
				}

			}).start();

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

		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

		for (;;) {
			var sumOperands = 0L;
			bb.clear(); // we clean buffer
			ByteBuffer bbLimitInt = bb.limit(INT_SIZE);
			if (!readFully(sc, bbLimitInt)) { // if we overflow size of int in bytes
				return; // we leave method
			}
			bb.flip(); // read-mode
			var nbOperands = bb.getInt(); // Reads the next four bytes at this buffer's current position,composing them
											// into an int value

			var i = 0;
			while (i < nbOperands) {
				bb.clear(); // write-mode after
				ByteBuffer bbLimitLong = bb.limit(LONG_SIZE);
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
	 * Version 2 with ensure method, allocate fixed size of buffer
	 * 
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void serve2(SocketChannel sc) throws IOException, InterruptedException {
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

	public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
		FixedPrestartedLongSumServer server = new FixedPrestartedLongSumServer(Integer.parseInt(args[0]),
				Integer.parseInt(args[1]));
		server.launch();
	}
}

package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BoundedOnDemandConcurrentLongSumServer {

	private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private static final int INT_SIZE = Integer.BYTES;
	private static final int LONG_SIZE = Long.BYTES;
	private final Semaphore semaphore;

	public BoundedOnDemandConcurrentLongSumServer(int port, int numberPermits) throws IOException {
		
		this.semaphore = new Semaphore(numberPermits);
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

	public void launch() throws IOException, InterruptedException {
		logger.info("Server started");
		while (!Thread.interrupted()) {
			semaphore.acquire(); // remove authorization into semaphore - check before accecpt we have an authorization available
			SocketChannel client = serverSocketChannel.accept();
			Thread thread = new Thread(() -> {
				try {
					logger.info("Connection accepted from " + client.getRemoteAddress());
					serve(client);
				} catch (IOException ioe) {
					logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
				} catch (InterruptedException ie) {
					logger.info("Server interrupted");
					return;
				} finally { // in all case 
					semaphore.release(); // return an authorization
					silentlyClose(client);
				}
			});
			thread.start(); 
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
		BoundedOnDemandConcurrentLongSumServer server = new BoundedOnDemandConcurrentLongSumServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.launch();
	}
}

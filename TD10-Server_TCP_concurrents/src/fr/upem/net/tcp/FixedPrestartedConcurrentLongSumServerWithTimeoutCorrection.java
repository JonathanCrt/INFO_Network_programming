package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedConcurrentLongSumServerWithTimeoutCorrection {

	private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private static final int INT_SIZE = Integer.BYTES;
	private static final int LONG_SIZE = Long.BYTES;
	private final int maxClients;
	private final int timeout;
	
	
	private final Thread[] threads;
	private ThreadDataCorrection[] threadData;
	private Thread monitor;
	private Thread console;

	public FixedPrestartedConcurrentLongSumServerWithTimeoutCorrection(int port, int numberPermits, int timeout)
			throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName() + " starts on port " + port);
		this.maxClients = numberPermits;
		this.threads = new Thread[maxClients];
		this.threadData = new ThreadDataCorrection[maxClients];
		this.timeout = timeout;
	}

	private void consoleRun() {

		try (var scan = new Scanner(System.in);) {
			while (scan.hasNextLine()) {
				switch (scan.nextLine()) {
				case "INFO":
					System.out.println("There are " + this.connectedClients()  + " connected clients."); // == nb workers threads
					break;
				case "SHUTDOWN":
					this.shutdown();
					break;
				case "SHUTDOWNNOW":
					this.shutdownNow();
					return;
				}
			}
		}
	}


	private int connectedClients() {
		int cpt = 0;
		for(ThreadDataCorrection td: threadData) {
			if(td.isActive()) {
				cpt++;
			}
		}
		return cpt;
		
	}
	
	/**
	 * prevent the acceptance of new clients
	 */
	private void shutdown() {
		try {
			serverSocketChannel.close();
		} catch (IOException e) {
			// ignore exception
		}
		
	}
	
	private void shutdownNow() {
		this.monitor.interrupt();
		try {
			serverSocketChannel.close();
			for(Thread t: threads) {
				t.interrupt();
			}
		} catch (IOException e) {
			// ignore exception
		}
	}
	
	

	private void monitorRun() {
		try {
			while (!Thread.interrupted()) {
				Thread.sleep(timeout);
				for (ThreadDataCorrection td : threadData) {
					td.closeIfTimeout(timeout);
				}
			}
		} catch (InterruptedException ie) {
			logger.info("Monitor thread has stopped.");
		} catch (IOException e) {
			e.getMessage();
		}
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */

	public void launch() throws IOException {

		Arrays.setAll(threadData, i -> new ThreadDataCorrection());
		logger.info("Server started");
		Arrays.setAll(threads, i ->

		new Thread(() -> {
			try {
				ThreadDataCorrection td = threadData[i];
				while (!Thread.interrupted()) {
					SocketChannel client = serverSocketChannel.accept();
					td.setSocketChannel(client);
					try {
						logger.info("Connection accepted from" + client.getRemoteAddress());
						serve(client, td);
						
					} catch (ClosedByInterruptException cie) {
						logger.info("Worker thread was asked to stop" + cie.getCause());
						return;
					} catch (IOException ioe) {
						logger.info("Connection terminated with client by IOException" + ioe.getCause());
					} 
					finally {
						td.close();
					}
				}

			} catch (AsynchronousCloseException e) {
				logger.severe("Worker thread was stopped");
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Worker thread was stopped", e.getCause());
			}
		}));

		for (Thread th : threads) {
			th.start();
		}
		this.monitor = new Thread(this::monitorRun);
		monitor.start();
		
		console = new Thread(this::consoleRun);
		console.start();
	}

	/**
	 * Treat the connection sc applying the protocole All IOException are thrown
	 * Warning : we allocate unbounded ressources !
	 * 
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void serve(SocketChannel sc, ThreadDataCorrection td) throws IOException{
		// we know we have 4 octets + 8 octets = number of longs + operands
		ByteBuffer bbInt = ByteBuffer.allocate(INT_SIZE);
		ByteBuffer bbLong = ByteBuffer.allocate(LONG_SIZE);
		while (!Thread.interrupted()) {
			bbInt.clear(); // read-mode --> write-mode
			if (!readFully(sc, bbInt, td)) {
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

			if (!readFully(sc, buff, td)) { // read until filled buffer
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
			td.tick();
			sc.write(bbLong);

		}
	}

	

	static boolean readFully(SocketChannel sc, ByteBuffer bb, ThreadDataCorrection threadData) throws IOException {
		while (bb.hasRemaining()) {
			threadData.tick();
			if (sc.read(bb) == -1) {
				logger.info("Input stream closed");
				return false;
			}

		}
		return true;
	}

	public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
		FixedPrestartedConcurrentLongSumServerWithTimeoutCorrection server = new FixedPrestartedConcurrentLongSumServerWithTimeoutCorrection(
				Integer.parseInt(args[0]), Integer.parseInt(args[1]), 2000);
		server.launch();
	}

}

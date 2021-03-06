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

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

	private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
	private final ServerSocketChannel serverSocketChannel;
	private static final int INT_SIZE = Integer.BYTES;
	private static final int LONG_SIZE = Long.BYTES;
	private final int maxClients;
	private final long timeout;
	private Thread monitorThread;
	
	private final Thread[] threads;
	private ThreadData[] threadData;
	

	public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int numberPermits, long timeout)
			throws IOException {
		this.maxClients = numberPermits;
		this.timeout = timeout;
		this.threads = new Thread[maxClients];
		this.threadData = new ThreadData[maxClients];
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

		Arrays.setAll(threadData, i -> new ThreadData());
		logger.info("Server started");
		Arrays.setAll(threads, i ->

		new Thread(() -> {
			try {
				ThreadData td = threadData[i];
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

		this.startMonitorThread(threadData, threads);
		this.consoleCommands(threadData, threads);
	}

	/**
	 * Start the monitor which will stop connection
	 * 
	 * @param threadData utility class
	 * @param threads workerThreads array of workers threads
	 */
	private void startMonitorThread(ThreadData[] threadData, Thread[] workersThreads) {
		this.monitorThread = new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					Thread.sleep(this.timeout);
				} catch (InterruptedException e) {
					// ignore
				}
				int idx = 0;
				while (idx < workersThreads.length) {
					try {
						threadData[idx].closeIfInactive(this.timeout);
					} catch (IOException e) {
						// ignore
					}
					idx++;
				}
			}
		});
		this.monitorThread.start();
	}

	/**
	 * helper method to interrupd all works threads
	 * 
	 * @param workerThreads array of workers threads
	 */
	private void interruptAllWorkersThreads(Thread[] workerThreads) {
		for (var th : workerThreads) {
			th.interrupt();
		}
	}

	
	private int connectedClients() {
		int cpt = 0;
		for(ThreadData td: threadData) {
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
	
	
	/**
	 * To command execution of server
	 * 
	 * @param threadData utility class
	 * @param threads array of workers threads
	 */
	private void consoleCommands(ThreadData[] threadData, Thread[] threads) {
		try (Scanner scanner = new Scanner(System.in)) {
			while (scanner.hasNext()) {
				String cmdUpperCase = scanner.next().toUpperCase(); // to avoid problem
				
				if (cmdUpperCase == "INFO") {
					System.out.println("There are " + this.connectedClients()  + " connected clients."); // == nb workers threads
					break;

				} else if (cmdUpperCase == "SHUTDOWNNOW") {
					logger.info("[SHUTDOWNNOW] : The Server was stopped.");
					this.monitorThread.interrupt();
					this.interruptAllWorkersThreads(threads);
					break;
				} else if (cmdUpperCase == "SHUTDOWN") {
					logger.info("[SHUTDOWN] Server stops after processing the existing clients.");
					
					this.shutdown();
					break;
				} else {
					logger.info("[ERROR] Command not exist.");
				}

			}

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
	public void serve(SocketChannel sc, ThreadData td) throws IOException{
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

	static boolean readFully(SocketChannel sc, ByteBuffer bb, ThreadData threadData) throws IOException {
		while (bb.hasRemaining()) {
			if (sc.read(bb) == -1) {
				logger.info("Input stream closed");
				return false;
			}
			threadData.tick();
		}
		return true;
	}

	public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
		FixedPrestartedConcurrentLongSumServerWithTimeout server = new FixedPrestartedConcurrentLongSumServerWithTimeout(
				Integer.parseInt(args[0]), Integer.parseInt(args[1]), 2000);
		server.launch();
	}
}

package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

	/**
	 * class which permit to detect inactive client + interrupt connection with
	 * client without kill his thread 
	 * Created by CRETE JONATHAN on 23/04/2020 at
	 * 01:44
	 *
	 */
	private class ThreadData {
		private SocketChannel sc;
		private long lastActivity;

		private final Object lastActvityLock = new Object();
		private final Object acceptationLock = new Object();
		private boolean serverCanAcceptClients = true;

		/**
		 * Modify the client stream managed by this thread;
		 * 
		 * @param client client given
		 */
		private void setSocketChannel(SocketChannel client) {
			this.sc = client; // Socket channels are safe for use by multiple concurrent threads! (javadoc)
			synchronized (clientsAcceptedLock) {
				numberAcceptedClients.increment();
			}
			this.tick();
		}

		/**
		 * Indicates that the client is active at the time of the call to this method;
		 */
		private void tick() {
			synchronized (lastActvityLock) {
				long time = System.currentTimeMillis();
				this.lastActivity = time;
			}
		}

		/**
		 * Disconnect the client if it is idle for more than timeout milliseconds
		 * 
		 * @param timeout timeout given
		 * @throws IOException
		 */
		private void closeIfInactive(long timeout) throws IOException {
			synchronized (lastActvityLock) {
				if (System.currentTimeMillis() > timeout + lastActivity) {
					this.close();
				}
			}
		}

		/**
		 * Disconnect the client
		 * 
		 * @throws IOException
		 */
		private void close() throws IOException {
			assert (sc != null);
			logger.info("The client " + sc.getRemoteAddress() + " was disconnected because of inactivity");
			this.sc.close(); // call close with client attached to sc
		}

	}

	private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private static final int INT_SIZE = Integer.BYTES;
	private static final int LONG_SIZE = Long.BYTES;
	private final int maxClients;
	private final Object clientsAcceptedLock = new Object(); // means clients connected
	private LongAdder numberAcceptedClients;
	private final long timeout;
	private Thread monitorThread;

	public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int numberPermits, long timeout)
			throws IOException {
		this.maxClients = numberPermits;
		this.timeout = timeout;
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

		ThreadData[] threadData = new ThreadData[maxClients];
		Thread[] threads = new Thread[maxClients];

		for (int i = 0; i < maxClients; i++) {
			int j = i; // lambda grrr

			threadData[i] = new ThreadData();
			threads[i] = new Thread(() -> {

				try {
					while (!Thread.interrupted()) {
						SocketChannel client = serverSocketChannel.accept();

						try {
							synchronized (threadData[j].acceptationLock) {
								if (!threadData[j].serverCanAcceptClients) {
									return;
								}
							}
							threadData[j].setSocketChannel(client);
							logger.info("Connection accepted from " + client.getRemoteAddress());

						} catch (IOException ie) {
							logger.info("Worker thread was interrupted.");
							break;
						}

						try {
							serve(client, threadData[j]);
						} catch (IOException ioe) {
							logger.info("Connection terminated with client by IOException" + ioe.getCause());
						} catch (InterruptedException ie) {
							logger.info("Server interrupted");
							return;
						} finally {
							synchronized (clientsAcceptedLock) {
								numberAcceptedClients.decrement(); // end of treatment, ready to next client
							}
							silentlyClose(client);
						}

					}
				} catch (AsynchronousCloseException e) {
					logger.severe("Worker thread was stopped");
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Worker thread was stopped", e.getCause());
				}

			});
			threads[i].start();
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
					synchronized (this.clientsAcceptedLock) {
						logger.info("[INFO] : There are" + numberAcceptedClients + " connected.");
					}
					break;

				} else if (cmdUpperCase == "SHUTDOWNNOW") {
					logger.info("[SHUTDOWNNOW] : The Server was stopped.");
					this.monitorThread.interrupt();
					this.interruptAllWorkersThreads(threads);
					break;
				} else if (cmdUpperCase == "SHUTDOWN") {
					logger.info("[SHUTDOWN] Server stops after processing the existing clients.");
					
					int index = 0;
					while(index < threadData.length) {
						synchronized (threadData[index].acceptationLock) {
							threadData[index].serverCanAcceptClients = false;
						}
						index++;
					}
					this.monitorThread.interrupt();
					break;
				} else {
					logger.info("[ERROR] Command not exist.");
				}

			}

		}
	}

	/**
	 * Treat the connection sc applying the protocole 
	 * All IOException are thrown
	 * 
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void serve(SocketChannel sc, ThreadData threadData) throws IOException, InterruptedException {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

		for (;;) {
			var sumOperands = 0L;
			bb.clear(); // we clean buffer
			ByteBuffer bbLimitInt = bb.limit(INT_SIZE);
			if (!readFully(sc, bbLimitInt, threadData)) { // if we overflow size of int in bytes
				return; // we leave method
			}
			bb.flip(); // read-mode
			var nbOperands = bb.getInt(); // Reads the next four bytes at this buffer's current position,composing them
											// into an int value

			var i = 0;
			while (i < nbOperands) {
				bb.clear(); // write-mode after
				ByteBuffer bbLimitLong = bb.limit(LONG_SIZE);
				if (!readFully(sc, bbLimitLong, threadData)) { // if we overflow size of long in bytes
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

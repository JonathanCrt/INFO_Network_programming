package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {
	public static final int BUFFER_SIZE = 1024;
	private static final Logger LOGGER = Logger.getLogger(ClientUpperCaseUDPRetry.class.getName());

	private static void usage() {
		System.out.println("Usage : NetcatUDP host port charset");
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		if (args.length != 3) {
			usage();
			return;
		}

		InetSocketAddress server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		Charset cs = Charset.forName(args[2]);
		var bb = ByteBuffer.allocate(BUFFER_SIZE);

		try (Scanner scan = new Scanner(System.in); DatagramChannel dc = DatagramChannel.open()) {
			dc.bind(null); // bind to a random available port

			var blockingQueue = new ArrayBlockingQueue<String>(20);

			/**
			 * LISTENER THREAD to receive a packet, decode it, and put it into a queue
			 */
			var listenerThread = new Thread(() -> {

				var receivedData = ByteBuffer.allocate(BUFFER_SIZE);
				while (!Thread.currentThread().isInterrupted()) {
					receivedData.clear();

					try {
						dc.receive(receivedData); //receive dc via a channel
					} catch (AsynchronousCloseException e) {
						LOGGER.warning("Asynchronous Exception");
					} catch (IOException e) {
						LOGGER.warning("Some I/O errors was occured.");
					}

					try {
						var decodedCharsetBuffer = cs.decode(receivedData); // decode buffer
						decodedCharsetBuffer.flip();
						blockingQueue.put(decodedCharsetBuffer.toString());
					} catch (InterruptedException e) {
						LOGGER.info("The Listener thread was interrupted.");
					}

				}

			});
			listenerThread.start();

			/**
			 * SENDER THREAD (main)
			 * 
			 */
			while (scan.hasNextLine()) {
				String msgLine = null;
				bb = cs.encode(scan.nextLine()); // encode line to bytes

				while (msgLine == null) {
					dc.send(bb, server); // send dataChannel
					msgLine = blockingQueue.poll(1, TimeUnit.SECONDS); // Retrieves & removes (Return) the head of the
																		// queue or null
					bb.flip();
				}
				System.out.println("Received message : " + msgLine);
			}
			listenerThread.interrupt();

			/*
			 * We have try-with-ressources scan.close(); dc.close();
			 */
		}

	}
}

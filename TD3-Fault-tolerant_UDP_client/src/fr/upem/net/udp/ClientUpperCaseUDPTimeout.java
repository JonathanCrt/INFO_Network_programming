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
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPTimeout {

	public static final int BUFFER_SIZE = 1024;
	private static final Logger LOGGER = Logger.getLogger(ClientUpperCaseUDPTimeout.class.getName());

	private static void usage() {
		System.out.println("Usage : NetcatUDP host port charset");
	}

	public static void main(String[] args) {
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
						dc.receive(receivedData);
					} catch (AsynchronousCloseException e) {
						LOGGER.warning("Asynchronous Exception");
					} catch (IOException e) {
						LOGGER.warning("Some I/O errors was occured.");
					}

					try {
						var decodedCharset = cs.decode(receivedData).flip().toString();
						blockingQueue.put(decodedCharset);
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
				var line = scan.nextLine();
				bb = cs.encode(line); // encode line to bytes
				dc.send(bb, server); // send dataChannel
				line = blockingQueue.poll(1, TimeUnit.SECONDS);
				bb.flip();

				if (line == null) {
					LOGGER.info("The server didn't respond.");
				} else {
					System.out.println("Received message : " + line);

				}
			}
			listenerThread.interrupt();

		} catch (InterruptedException e) {
			LOGGER.warning("Sender Thread : sender thread was interrupted when it waiting");
		} catch (IOException e) {
			LOGGER.warning("Sender Thread : Some I/O errors was occured.");
		}

	}
}

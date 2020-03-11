package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientUpperCaseUDPFile {

	private static final Charset UTF8 = Charset.forName("UTF8");
	private static final int BUFFER_SIZE = 1024;
	private static final Logger LOGGER = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());

	private static void usage() {
		System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 5) {
			usage();
			return;
		}

		String inFilename = args[0];
		String outFilename = args[1];
		int timeout = Integer.valueOf(args[2]);
		String host = args[3];
		int port = Integer.valueOf(args[4]);
		SocketAddress dest = new InetSocketAddress(host, port);

		// Read all lines of inFilename opened in UTF-8
		List<String> lines = Files.readAllLines(Paths.get(args[0]), UTF8);
		ArrayList<String> upperCaseLines = new ArrayList<>();

		var queue = new ArrayBlockingQueue<String>(20);

		try (var datagramChannel = DatagramChannel.open()) {
			datagramChannel.bind(null);

			var listenerThread = new Thread(() -> {
				var receivedData = ByteBuffer.allocate(BUFFER_SIZE);
				while (!Thread.currentThread().isInterrupted()) {
					receivedData.clear();

					try {
						datagramChannel.receive(receivedData); // receive dc via a channel
					} catch (AsynchronousCloseException e) {
						LOGGER.warning("Asynchronous Exception");
					} catch (IOException e) {
						LOGGER.warning("Some I/O errors was occured.");
					}

					try {
						var decodedCharsetBuffer = UTF8.decode(receivedData); // decode buffer
						decodedCharsetBuffer.flip();
						queue.put(decodedCharsetBuffer.toString());
					} catch (InterruptedException e) {
						LOGGER.info("The Listener thread was interrupted.");
					}

				}
			});
			listenerThread.start();

			/**
			 * SENDER THREAD
			 */
			for (String line : lines) { // For each line of List
				String msgLine = null; // msg init
				var encodedBufferToSend = UTF8.encode(line); // we encode data into buffer
				while (msgLine == null) {
					datagramChannel.send(encodedBufferToSend, dest);
					msgLine = queue.poll(timeout, TimeUnit.MILLISECONDS);
					encodedBufferToSend.flip();
				}
				upperCaseLines.add(msgLine); // add line to ArrayList
			}
			listenerThread.interrupt();

		}

		// Write upperCaseLines to outFilename in UTF-8
		Files.write(Paths.get(outFilename), upperCaseLines, UTF8, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}
}

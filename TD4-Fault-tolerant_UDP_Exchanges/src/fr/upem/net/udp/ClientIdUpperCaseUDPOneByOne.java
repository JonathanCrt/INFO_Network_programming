package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.nio.sctp.SendFailedNotification;

public class ClientIdUpperCaseUDPOneByOne {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
	private final List<String> lines;
	private final List<String> upperCaseLines = new ArrayList<>(); //
	private final int timeout;
	private final String outFilename;
	private final InetSocketAddress serverAddress;
	private final DatagramChannel dc;

	private final BlockingQueue<Response> queue = new SynchronousQueue<>();

	private static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
	}

	private ClientIdUpperCaseUDPOneByOne(List<String> lines, int timeout, InetSocketAddress serverAddress,
			String outFilename) throws IOException {
		this.lines = lines;
		this.timeout = timeout;
		this.outFilename = outFilename;
		this.serverAddress = serverAddress;
		this.dc = DatagramChannel.open();
		dc.bind(null);
	}

	private void listenerThreadRun() {
		try {
			var receivedData = ByteBuffer.allocate(BUFFER_SIZE);
			while (!Thread.currentThread().isInterrupted()) {
				receivedData.clear();
				dc.receive(receivedData);
				receivedData.flip();
				var id = receivedData.getLong();
				var decodedData = UTF8.decode(receivedData).toString(); // The buffer contains long id
				queue.put(new Response(id, decodedData)); // put response into
															// queue with id
															// + msg
			}
		} catch (InterruptedException | AsynchronousCloseException e) {
			logger.info("Listener thread was interrupted");
			return;
		} catch (IOException e) {
			logger.log(Level.WARNING, "Some I/O errors was occured.", e);
		}
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
		InetSocketAddress serverAddress = new InetSocketAddress(host, port);

		// Read all lines of inFilename opened in UTF-8
		List<String> lines = Files.readAllLines(Paths.get(inFilename), UTF8);
		// Create client with the parameters and launch it
		ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress,
				outFilename);
		client.launch();

	}

	private void launch() throws IOException, InterruptedException {
		Thread listenerThread = new Thread(this::listenerThreadRun);
		listenerThread.start();

		var id = 0;
		long lastSend = 0;
		var sentDataBuffer = ByteBuffer.allocate(BUFFER_SIZE);

		for (var line : lines) {

			sentDataBuffer.clear();
			var encodedLine = UTF8.encode(line);
			sentDataBuffer.putLong(id++).put(encodedLine); // Encode create a ByteBuffer
			Response response = null;
			while (response == null || response.id != id - 1) {
				var currentTime = System.currentTimeMillis();

				if (currentTime - lastSend >= timeout) {
					dc.send(sentDataBuffer.flip(), serverAddress);
					lastSend = currentTime;
				}
				response = queue.poll(timeout - (currentTime - lastSend), TimeUnit.MILLISECONDS);

			}
			upperCaseLines.add(response.msg);
		}

		listenerThread.interrupt();
		dc.close();

		Files.write(Paths.get(outFilename), upperCaseLines, UTF8, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static class Response {
		long id;
		String msg;

		Response(long id, String msg) {
			this.id = id;
			this.msg = msg;
		}
	}
}

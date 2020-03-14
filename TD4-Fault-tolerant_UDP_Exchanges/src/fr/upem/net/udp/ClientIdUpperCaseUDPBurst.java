package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
	private static final Charset UTF8 = Charset.forName("UTF8");
	private static final int BUFFER_SIZE = 1024;
	private final List<String> lines;
	private final int nbLines;
	private final String[] upperCaseLines; //
	private final int timeout;
	private final String outFilename;
	private final InetSocketAddress serverAddress;
	private final DatagramChannel dc;
	private final BitSet received; // BitSet marking received requests

	private static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
	}

	private ClientIdUpperCaseUDPBurst(List<String> lines, int timeout, InetSocketAddress serverAddress,
			String outFilename) throws IOException {
		this.lines = lines;
		this.nbLines = lines.size();
		this.timeout = timeout;
		this.outFilename = outFilename;
		this.serverAddress = serverAddress;
		this.dc = DatagramChannel.open();
		dc.bind(null);
		this.received = new BitSet(nbLines);
		this.upperCaseLines = new String[nbLines];
	}

	private void senderThreadRun() {

		try {
			var sentDataBuffer = ByteBuffer.allocate(BUFFER_SIZE);
			while (!Thread.currentThread().isInterrupted()) {
				for (var index = 0; index < nbLines; index++) {

					synchronized (received) {
						if (received.get(index)) {
							continue;
						}
					}
					var encodedLineAtIndex = UTF8.encode(lines.get(index));
					sentDataBuffer.clear().putLong(index).put(encodedLineAtIndex);
					dc.send(sentDataBuffer.flip(), serverAddress);
				}
				Thread.sleep(timeout);
			}
		} catch (InterruptedException | AsynchronousCloseException e) {
			logger.info("Sender thread was interrupted");
			return;
		} catch (IOException e) {
			logger.log(Level.WARNING, "Some I/O errors was occured.", e);
		}

	}

	private void launch() throws IOException {
		Thread senderThread = new Thread(this::senderThreadRun);
		senderThread.start();

		try {
			var receivedData = ByteBuffer.allocate(BUFFER_SIZE);

			for (;;) {
				synchronized (this.received) {
					if (received.cardinality() == this.nbLines) {
						break;
					}
				}
				receivedData.clear();
				dc.receive(receivedData);
				var id = (int) receivedData.flip().getLong();
				upperCaseLines[id] = UTF8.decode(receivedData).toString();
				synchronized (this.received) {
					received.set(id);
				}
			}

		} catch (IOException e) {
			logger.log(Level.WARNING, "Some I/O error was occured.", e);
		}
		senderThread.interrupt();
		dc.close();

		Files.write(Paths.get(outFilename), Arrays.asList(upperCaseLines), UTF8, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

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
		ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, outFilename);
		client.launch();

	}
}
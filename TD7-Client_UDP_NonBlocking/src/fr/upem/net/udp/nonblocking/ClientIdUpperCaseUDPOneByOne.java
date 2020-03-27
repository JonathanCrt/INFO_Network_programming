package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
	private static final Charset UTF8 = Charset.forName("UTF8");
	private static final int BUFFER_SIZE = 1024;

	private enum State {
		SENDING, RECEIVING, FINISHED
	};

	private final List<String> lines;
	private final List<String> upperCaseLines = new ArrayList<>();
	private final int timeout;
	private final InetSocketAddress serverAddress;
	private final DatagramChannel dc;
	private final Selector selector;
	private final SelectionKey uniqueKey;
	
	private final ByteBuffer SenderBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final ByteBuffer receivedBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	
	private State state;
	private long lastSend;

	private static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
	}

	public ClientIdUpperCaseUDPOneByOne(List<String> lines, int timeout, InetSocketAddress serverAddress)
			throws IOException {
		this.lines = lines;
		this.timeout = timeout;
		this.serverAddress = serverAddress;
		this.dc = DatagramChannel.open();
		dc.configureBlocking(false);
		dc.bind(null);
		this.selector = Selector.open();
		this.uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
		this.state = State.SENDING;
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
		ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress);
		List<String> upperCaseLines = client.launch();
		Files.write(Paths.get(outFilename), upperCaseLines, UTF8, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);

	}

	private List<String> launch() throws IOException, InterruptedException {
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!isFinished()) {
			selector.select(updateInterestOps());
			for (SelectionKey key : selectedKeys) {
				if (key.isValid() && key.isWritable()) {
					doWrite();
				}
				if (key.isValid() && key.isReadable()) {
					doRead();
				}
			}

			selectedKeys.clear();
		}
		dc.close();
		return upperCaseLines;
	}

	/**
	 * Updates the interestOps on key based on state of the context
	 *
	 * @return the timeout for the next select (0 means no timeout)
	 */

	private int updateInterestOps() {
		var time = System.currentTimeMillis();
		if (this.state == State.SENDING || time > this.lastSend + this.timeout) { // Envoi 
			this.uniqueKey.interestOps(SelectionKey.OP_WRITE);
			return 0;
		}
		if (state == State.RECEIVING) { // Réception
			uniqueKey.interestOps(SelectionKey.OP_READ);
		}
		var delay = (int) (this.lastSend + this.timeout - time);
		return delay; // Temps qu'on est prêt à passer dans le select()
	}

	private boolean isFinished() {
		return state == State.FINISHED;
	}

	/**
	 * Performs the receptions of packets
	 * reçu la réponse à la question = passer à l'etat je veux envoyer.
	 * @throws IOException
	 */

	private void doRead() throws IOException {
		// TODO
	}

	/**
	 * Tries to send the packets
	 *
	 * @throws IOException
	 */

	private void doWrite() throws IOException {
		// TODO
	}
}

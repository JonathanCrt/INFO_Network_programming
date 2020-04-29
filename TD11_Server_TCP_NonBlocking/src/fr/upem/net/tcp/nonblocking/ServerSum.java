package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

public class ServerSum {

	static private int BUFFER_SIZE = 2 * Integer.BYTES;
	static private Logger logger = Logger.getLogger(ServerSum.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public static int INT_SIZE = Integer.BYTES;

	public ServerSum(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}
	
	/**
	 * blocks until an incoming packet dice will get a packet selector  completed all selected keys with the key to our selector -> 
	 * method call treatkey
	 * @throws IOException
	 */
	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		try {
			while (!Thread.interrupted()) {
				printKeys(); // for debug
				System.out.println("Starting select");
				selector.select(this::treatKey);
				System.out.println("Select finished");
			}
		} catch (UncheckedIOException tunneled) {
			throw tunneled.getCause();
		}
		
	}

	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug

		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			logger.severe("ServerSumOneShot have a serious problem.");
			this.silentlyClose(key);
			throw new UncheckedIOException(ioe); // serious problem
		}

		try {
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		} catch (IOException ioe) {
			logger.info("Error has occured while treating the client");
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null) {
			logger.warning("The selector gave a wrong hint.");
			return; // the selector gave a bad hint
		}
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
	}
	/**
	 * return read-mode
	 * @param key
	 * @throws IOException
	 */
	private void doRead(SelectionKey key) throws IOException {

		SocketChannel sc = (SocketChannel) key.channel();
		ByteBuffer bb = (ByteBuffer) key.attachment();

		if (sc.read(bb) == -1) { // client closed connection and we haven't all data
			logger.info("Client closed the connection");
			this.silentlyClose(key);
		}
		// Write-mode, working area after data
		if (bb.hasRemaining()) {
			return;
		}
		bb.flip(); // read-mode
		int sum = bb.getInt() + bb.getInt();
		bb.clear();
		bb.putInt(sum);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	
	private void doWrite(SelectionKey key) throws IOException {
		// need to be in read-mode
		SocketChannel sc = (SocketChannel) key.channel();
		ByteBuffer bb = (ByteBuffer) key.attachment();

		bb.flip();
		sc.write(bb);
		bb.compact();
		if (bb.position() != 0) {
			return; // need a new writing
		}
		key.interestOps(SelectionKey.OP_READ);
	}
	
	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerSum(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSum port");
	}

	/***
	 * Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println(
					"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}
}
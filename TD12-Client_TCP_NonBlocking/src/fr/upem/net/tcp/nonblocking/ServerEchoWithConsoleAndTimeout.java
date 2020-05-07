package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerEchoWithConsoleAndTimeout {

	static private class Context {

		final private SelectionKey key;
		final private SocketChannel sc;
		final private ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		private boolean clientClosedConnection = false;
		private boolean activeSinceLastTimeoutCheck = true;

		private Context(SelectionKey key){
			this.key = key;
			this.sc = (SocketChannel) key.channel();
		}

		/**
		 * Update the interestOps of the key looking
		 * only at values of the boolean closed and
		 * the ByteBuffer buff.
		 *
		 * The convention is that buff is in write-mode.
		 */
		private void updateInterestOps() {
			
			int interestOps = 0;
			if(!clientClosedConnection && bb.hasRemaining()) {
				interestOps = interestOps | SelectionKey.OP_READ; // set to 1, bit corresponding to read operation
			}
			if(bb.position() != 0) {
				interestOps = interestOps | SelectionKey.OP_WRITE; // set to 1, bit corresponding to write operation
			}
			if(interestOps == 0) {
				this.silentlyClose();
				return;
			}
			key.interestOps(interestOps);
			
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that buff is in write-mode before calling doRead
		 * and is in write-mode after calling doRead
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {
			this.activeSinceLastTimeoutCheck = true;
			if(sc.read(bb) == -1) {
				this.clientClosedConnection = true; // client has nothing to send
			}
			this.updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that buff is in write-mode before calling doWrite
		 * and is in write-mode after calling doWrite
		 *
		 * @throws IOException
		 */
		private void doWrite() throws IOException {
			this.activeSinceLastTimeoutCheck  = true;
			this.bb.flip();
			this.sc.write(bb);
			bb.compact(); // read-mode --> write-mode
			this.updateInterestOps(); // buffer should be in write-mode
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}
	}

	static private int BUFFER_SIZE = 1_024;
	static private Logger logger = Logger.getLogger(ServerEchoWithConsoleAndTimeout.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private enum Console {INFO, SHUTDOWN, SHUDOWNNOW}
	private boolean serverIsShutdown;
	private final BlockingQueue<Console> consoleQueue = new ArrayBlockingQueue<Console>(100);
	private SelectionKey serverSelectedKey;
	private Long lastTimeoutCheck;
	public static long TIMEOUT = 10000;
	

	public ServerEchoWithConsoleAndTimeout(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		this.serverSelectedKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		Thread consoleThread = new Thread(() -> {
			try(Scanner scanner = new Scanner(System.in)){
				while(scanner.hasNext()) {
					switch (scanner.next().toUpperCase()) {
					case "INFO":
						this.consoleQueue.add(Console.INFO);
						this.selector.wakeup();
						break;
					case "SHUTDOWN":
						this.consoleQueue.add(Console.SHUTDOWN);
						this.selector.wakeup();
						return;
					case "SHUTDOWNNOW":
						this.consoleQueue.add(Console.SHUDOWNNOW);
						this.selector.wakeup();
						return;
					default:
						throw new IllegalArgumentException("Unexpected command: " + scanner.next().toUpperCase());
					}
				}
			}
		});
		consoleThread.start();
		
		this.lastTimeoutCheck = System.currentTimeMillis();
		while(!Thread.interrupted()) {
			this.printKeys(); // for debug
			System.out.println("Starting select");
			try {
				
				if(this.getNumberOfKeys() == 0 && this.serverIsShutdown) {
					logger.severe("Server have a problem");
					return;
				}
				
				if(TIMEOUT + lastTimeoutCheck <= System.currentTimeMillis()) {
					this.doDisconnection();
					this.lastTimeoutCheck = System.currentTimeMillis();
				}
				
				
				selector.select(this::treatKey, TIMEOUT);
				Console retrievedConsoleCommand = consoleQueue.poll();
				if(retrievedConsoleCommand != null) {
					if(retrievedConsoleCommand == Console.INFO) {
						System.out.println("There are " + this.connectedClients()  + " connected clients."); 
					}
					if(retrievedConsoleCommand == Console.SHUTDOWN) {
						this.silentlyClose(this.serverSelectedKey);
						this.serverIsShutdown = true;
					}
					if(retrievedConsoleCommand == Console.SHUDOWNNOW) {
						this.shutdownNowAllTreatments();
						this.serverIsShutdown = true;
					}
				}
				
				
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
	}
	
	
	private int connectedClients() {
		Long cpt = 0L;
		cpt = this.selector.keys()
		.stream()
		.filter(k -> k.isValid() && !k.isAcceptable())
		.count();
		return cpt.intValue();
	}

	private void shutdownNowAllTreatments() {
		this.selector.keys()
		.stream()
		.filter(eachKey -> serverSelectedKey != eachKey)
		.forEach(eachKey -> ((Context) eachKey.attachment()).silentlyClose());
	}
	
	private int getNumberOfKeys() {
		Long cpt = 0L;
		cpt = this.selector.keys()
				.stream()
				.filter(k -> k.isValid())
				.count();
		return cpt.intValue();
	}
	
	private void doDisconnection() {
		this.selector.keys()
		.stream()
		.filter(k -> k.attachment() != null)
		.forEach(currentKey -> {
			Context context = ((Context) currentKey.attachment());
			if(context.activeSinceLastTimeoutCheck) {
				context.activeSinceLastTimeoutCheck = false;
			} else {
				context.silentlyClose();
			}
		});
	}
	
	
	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch(IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO,"Connection closed with client due to IOException",e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		var ssc = serverSocketChannel.accept();
		if(ssc == null) {
			return;
		}
		ssc.configureBlocking(false);
		SelectionKey clientKey = ssc.register(this.selector, SelectionKey.OP_READ);
		clientKey.attach(new Context(clientKey));// we attach context to client
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
		if (args.length!=1){
			usage();
			return;
		}
		new ServerEchoWithConsoleAndTimeout(Integer.parseInt(args[0])).launch();
	}

	private static void usage(){
		System.out.println("Usage : ServerEcho port");
	}

	/***
	 *  Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key){
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
		if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
		if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
		return String.join("|",list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet){
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e){
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable()) list.add("ACCEPT");
		if (key.isReadable()) list.add("READ");
		if (key.isWritable()) list.add("WRITE");
		return String.join(" and ",list);
	}
}

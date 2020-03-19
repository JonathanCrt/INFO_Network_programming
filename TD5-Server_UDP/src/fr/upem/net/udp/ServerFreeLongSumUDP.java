package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.logging.Logger;

public class ServerFreeLongSumUDP {
	
	private static final Logger logger = Logger.getLogger(ServerLongSumUDP.class.getName());
	private static final int BUFFER_SIZE = 1024;

	private final DatagramChannel dc;
	private final HashMap<InetSocketAddress, HashMap<Long, SumData>> cltData = new HashMap<>();
	private static final byte ACK = 2;
	private static final byte RESPONSE = 3;
	private static final byte CLEAN = 4;
	private static final byte CLEAN_ACK = 5;

	
	public ServerFreeLongSumUDP(int port) throws IOException {
		this.dc = DatagramChannel.open();
		this.dc.bind(new InetSocketAddress(port));
		logger.info("ServerBetterUpperCaseUDP started on port " + port);
	}

	private void sendResponsePacket(SumData sumData, long sessionID, InetSocketAddress targetPortIPClient) throws IOException {
		var sentBuffer = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + Long.BYTES);
		sentBuffer.put(RESPONSE);
		sentBuffer.putLong(sessionID);
		sentBuffer.putLong(sumData.partialSum);
		sentBuffer.flip();
		dc.send(sentBuffer, targetPortIPClient);
	}

	private void sendResponsePacket(SumData sumData, long sessionID, int idPositionOperand, InetSocketAddress targetPortIPClient)
			throws IOException {
		synchronized (sumData.bitSetOperands) {
			if (sumData.hasReceivedAllOperands()) {
				sendResponsePacket(sumData, sessionID, targetPortIPClient);
			} else {
				sendACKPacket(idPositionOperand, sessionID, targetPortIPClient);
			}
		}
	}
	
	private void sendACKPacket(int idPositionOperand, long sessionID, InetSocketAddress targetPortIPClient) throws IOException {
		var sentBuffer = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + Long.BYTES);
		sentBuffer.put(ACK);
		sentBuffer.putLong(sessionID);
		sentBuffer.putLong(idPositionOperand);
		sentBuffer.flip();
		dc.send(sentBuffer, targetPortIPClient);
	}

	private void clearSession(long sessionID, InetSocketAddress targetPortIPClient) throws IOException {
		cltData.get(targetPortIPClient).remove(sessionID);
		var sentBuffer = ByteBuffer.allocate(Byte.BYTES + Long.BYTES);
		sentBuffer.put(CLEAN_ACK);
		sentBuffer.putLong(sessionID);
		sentBuffer.flip();
		dc.send(sentBuffer, targetPortIPClient);
	}


	public void serve() throws IOException {
		var receivedBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
		while (!Thread.interrupted()) {
			receivedBuffer.clear();
			var targetPortIPClient = (InetSocketAddress) dc.receive(receivedBuffer);
			receivedBuffer.flip();

			var OperandID = receivedBuffer.get();
			var sessionID = receivedBuffer.getLong();

			if (OperandID == CLEAN) {
				clearSession(sessionID, targetPortIPClient);
			} else {
				var idPositionOperand = (int) receivedBuffer.getLong();
				var totalOperand = receivedBuffer.getLong();
				var operandValue = receivedBuffer.getLong();

				logger.info(
						"Received: SessionID ->" + sessionID + 
						" idPositionOperand -> " + idPositionOperand + 
						" OperandValue -> " + operandValue
						);

				var sumData = cltData.computeIfAbsent(targetPortIPClient, key -> new HashMap<>()).computeIfAbsent(sessionID,
						value -> new SumData(totalOperand));
				System.out.println("ttOps : " + totalOperand);
				sumData.update(operandValue, idPositionOperand);
				sendResponsePacket(sumData, sessionID, idPositionOperand, targetPortIPClient);
			}
		}
		dc.close();
	}

	
	private static class SumData {
		private final long nbTotalElements;
		private final BitSet bitSetOperands;
		private long partialSum;

		SumData(long nbTotalElements) {
			this.nbTotalElements = nbTotalElements;
			this.bitSetOperands = new BitSet((int) nbTotalElements);
		}
		
		private boolean hasReceivedAllOperands() {
			synchronized (bitSetOperands) {
				return this.bitSetOperands.cardinality() == this.nbTotalElements;
			}

		}
		public void update(long value, long idPositionOperand) {
			synchronized (bitSetOperands) {
				var bitAtIndex = this.bitSetOperands.get((int) idPositionOperand);
				if (!bitAtIndex) {
					this.bitSetOperands.set((int) idPositionOperand);
					this.partialSum += value;
				}
			}

		}
	}
	
	
	public static void usage() {
		System.out.println("Usage : ServerFreeLongSum port");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		ServerFreeLongSumUDP server;
		int port = Integer.valueOf(args[0]);
		if (!(port >= 1024) & port <= 65535) {
			logger.severe("The port number must be between 1024 and 65535");
			return;
		}
		try {
			server = new ServerFreeLongSumUDP(port);
		} catch (BindException e) {
			logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
			return;
		}
		server.serve();
	}
}

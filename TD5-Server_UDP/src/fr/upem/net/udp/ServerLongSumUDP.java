package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerLongSumUDP {

	private static final Logger LOGGER = Logger.getLogger(ServerLongSumUDP.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final DatagramChannel dc;
	private final ByteBuffer buffRec = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final ByteBuffer buffSend = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final HashMap<InetSocketAddress, HashMap<Long, SumData>> cltData;

	public ServerLongSumUDP(int port) throws IOException {
		this.dc = DatagramChannel.open();
		this.dc.bind(new InetSocketAddress(port));
		this.cltData = new HashMap<InetSocketAddress, HashMap<Long, SumData>>();
		LOGGER.info("ServerLongSum started on port " + port);

	}

	private SumData treatOperands(InetSocketAddress targetPortIPClient, long sessionID, long IDPositionOperand,
			long operandValue, long totalOperands) {

		var sumData = cltData.computeIfAbsent(targetPortIPClient, value -> new HashMap<>()).computeIfAbsent(sessionID,
				value -> new SumData(totalOperands));
		System.out.println("ttOps : " + totalOperands);
		sumData.update(operandValue, IDPositionOperand);

		return sumData;
	}

	public ByteBuffer createACK(long sessionID, long IDPositionOperand, long operandValue, long totalOperands) {
		this.buffSend.clear();

		var num_2 = Byte.valueOf("2");
		this.buffSend.put(num_2);
		this.buffSend.putLong(sessionID);
		this.buffSend.putLong(IDPositionOperand);
		this.buffSend.flip();

		return this.buffSend;
	}

	public ByteBuffer createResponse(SumData sumData, long sessionID, long IDPositionOperand, long operandValue,
			long totalOperands) {
		this.buffSend.clear();

		var num_3 = Byte.valueOf("3");
		this.buffSend.put(num_3);
		this.buffSend.putLong(sessionID);
		this.buffSend.putLong(sumData.partialSum);
		this.buffSend.flip();
		return this.buffSend;
	}

	public void serve() {

		try {
			while (!Thread.interrupted()) {
				this.buffRec.clear();
				InetSocketAddress targetPortIPClient = (InetSocketAddress) dc.receive(this.buffRec);

				this.buffRec.flip();

				// 1) Treat operands
				var idReq = this.buffRec.get();
				LOGGER.info("idReq : " + idReq);
				if (idReq != 1) {
					LOGGER.warning("The received packet is not correct");
					continue;
				}

				var sessionID = this.buffRec.getLong();
				var IDPositionOperand = this.buffRec.getLong();
				var operandValue = this.buffRec.getLong();
				var totalOperands = this.buffRec.getLong();

				var sumData = this.treatOperands(targetPortIPClient, sessionID, IDPositionOperand, totalOperands, operandValue);

				// 3) Response
				if (sumData.hasReceivedAllOperands()) {
					System.out.println("All operands are received");
					dc.send(this.createResponse(sumData, sessionID, IDPositionOperand, operandValue, totalOperands),
							targetPortIPClient);
				} else {
					// 2) send ACK
					dc.send(this.createACK(sessionID, IDPositionOperand, operandValue, totalOperands),
							targetPortIPClient);
				}

			}
			dc.close();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Some I/O errors was occured", e);
		}

	}

	private static class SumData {
		private long nbTotalElements;
		private BitSet bitSetOperands;
		private long partialSum;

		SumData(long nbOperations) {
			this.nbTotalElements = nbOperations;
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
		System.out.println("Usage : ServerLongSum port");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		ServerLongSumUDP server;
		int port = Integer.valueOf(args[0]);
		if (!(port >= 1024) & port <= 65535) {
			LOGGER.severe("The port number must be between 1024 and 65535");
			return;
		}
		try {
			server = new ServerLongSumUDP(port);
		} catch (BindException e) {
			LOGGER.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
			return;
		}
		server.serve();
	}

}

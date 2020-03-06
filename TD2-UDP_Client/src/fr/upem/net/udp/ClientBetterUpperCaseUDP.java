package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientBetterUpperCaseUDP {

	private static final Logger logger = Logger.getLogger(ClientBetterUpperCaseUDP.class.getName());
	private static final int MAX_PACKET_SIZE = 1024;

	private static Charset ASCII_CHARSET = StandardCharsets.US_ASCII; // Charset.forName("US-ASCII");

	/**
	 * Creates and returns a String message represented by the ByteBuffer buffer,
	 * encoded in the following representation: - the size (as a Big Indian int) of
	 * a charsetName encoded in ASCII<br/>
	 * - the bytes encoding this charsetName in ASCII<br/>
	 * - the bytes encoding the message in this charset.<br/>
	 * The accepted ByteBuffer buffer must be in <strong>write mode</strong> (i.e.
	 * need to be flipped before to be used).
	 *
	 * @param buffer a ByteBuffer containing the representation of an encoded String
	 *               message
	 * @return the String represented by buffer, or nothing if the buffer cannot be
	 *         decoded
	 */
	public static Optional<String> decodeMessage(ByteBuffer buffer) {

		buffer.flip(); // set position at beginning
		if (buffer.remaining() < Integer.BYTES) {
			return Optional.empty();
		}

		var sizeReceivedBuffer = buffer.getInt(); // get buffer size
		if (sizeReceivedBuffer <= 0 || sizeReceivedBuffer > buffer.remaining()) {
			return Optional.empty();
		}

		var charsetBuffer = ByteBuffer.allocate(sizeReceivedBuffer); // Allocation
		while (charsetBuffer.hasRemaining()) { // while we have access
			charsetBuffer.put(buffer.get()); // we set all bytes in new buffer
		}

		charsetBuffer.flip(); // set position at beginning
		var charsetName = ASCII_CHARSET.decode(charsetBuffer).toString(); // decoding charset name bytes --> string
		if (!Charset.isSupported(charsetName)) { // check if charset is supported
			return Optional.empty();
		}

		return Optional.of(Charset.forName(charsetName).decode(buffer).toString());

	}

	/**
	 * Creates and returns a new ByteBuffer containing the encoded representation of
	 * the String <code>msg</code> using the charset <code>charsetName</code> in the
	 * following format: - the size (as a Big Indian int) of the charsetName encoded
	 * in ASCII<br/>
	 * - the bytes encoding this charsetName in ASCII<br/>
	 * - the bytes encoding the String msg in this charset.<br/>
	 * The returned ByteBuffer is in <strong>write mode</strong> (i.e. need to be
	 * flipped before to be used). If the buffer is larger than MAX_PACKET_SIZE
	 * bytes, then returns Optional.empty.
	 *
	 * @param msg         the String to encode
	 * @param charsetName the name of the Charset to encode the String msg
	 * @return a newly allocated ByteBuffer containing the representation of msg, or
	 *         Optional.empty if the buffer would be larger than 1024
	 */
	public static Optional<ByteBuffer> encodeMessage(String msg, String charsetName) {
		// preconditions
		Objects.requireNonNull(msg);
		Objects.requireNonNull(charsetName);

		var sizeOfCharsetNameASCII = ASCII_CHARSET.encode(charsetName).remaining();
		var bytesEncodingCharsetName = ASCII_CHARSET.encode(charsetName);
		var bytesEncodingMessage = Charset.forName(charsetName).encode(msg);

		var bytesEncodedCapacity = bytesEncodingCharsetName.capacity() + bytesEncodingMessage.capacity();

		if ((Byte.BYTES + bytesEncodedCapacity) > MAX_PACKET_SIZE) {
			return Optional.empty();
		}

		var buff = ByteBuffer.allocate(MAX_PACKET_SIZE);
		return Optional.of(buff.putInt(sizeOfCharsetNameASCII).put(bytesEncodingCharsetName).put(bytesEncodingMessage));

	}

	public static void usage() {
		System.out.println("Usage : ClientBetterUpperCaseUDP host port charsetName");
	}

	public static void main(String[] args) throws IOException {

		// check and retrieve parameters
		if (args.length != 3) {
			usage();
			return;
		}
		String host = args[0];
		int port = Integer.valueOf(args[1]);
		String charsetName = args[2];

		SocketAddress dest = new InetSocketAddress(host, port);
		// buff to receive messages
		ByteBuffer buff = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);

		try (Scanner scan = new Scanner(System.in); DatagramChannel dc = DatagramChannel.open()) {
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				Optional<ByteBuffer> enc = encodeMessage(line, charsetName);
				if (!enc.isPresent()) {
					System.out.println("Line is too long to be sent using the protocol BetterUpperCase");
					continue;
				}
				ByteBuffer packet = enc.get();
				packet.flip();
				dc.send(packet, dest);
				buff.clear();
				dc.receive(buff);
				Optional<String> res = decodeMessage(buff);
				if (res.isPresent()) {
					System.out.println("Received: " + res.get());
				} else {
					System.out.println("Received an invalid paquet");
				}

			}
		}
	}

}
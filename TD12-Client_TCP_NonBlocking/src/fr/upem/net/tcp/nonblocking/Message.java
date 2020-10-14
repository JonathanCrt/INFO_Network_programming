package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Message {
	private final String login;
	private final String message;
	private final static Charset UTF8 = Charset.forName("UTF-8");

	public Message(String login, String message) {
		this.login = login;
		this.message = message;
	}

	public ByteBuffer toByteBuffer() {
		var bbLogin = UTF8.encode(login);
		var bbMessage = UTF8.encode(message);
		var bb = ByteBuffer.allocate(2 * Integer.BYTES + bbLogin.limit() + bbMessage.limit());
		bb.putInt(bbLogin.limit()).put(bbLogin).putInt(bbMessage.limit()).put(bbMessage);
		bb.flip();
		return bb;
	}

}

package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class StringReader implements Reader<String> {

	private enum State {
		DONE, WAITING_SIZE, WAITING_TEXT, ERROR
	};

	private State state = State.WAITING_SIZE;
	private final ByteBuffer internalbb;
	private int size;
	private String textValue;
	public static final Charset UTF8 = Charset.forName("UTF-8");
	private IntReader intReader;
	public static int MAX_BUFFER_SIZE = 1024;

	public StringReader() {
		this.internalbb = ByteBuffer.allocate(MAX_BUFFER_SIZE); // write-mode
		this.intReader = new IntReader();
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}

		var processInt = intReader.process(bb);
		if (processInt != ProcessStatus.DONE) {
			return processInt;
		}

		int size = intReader.get();
		if (size > MAX_BUFFER_SIZE) {
			return ProcessStatus.ERROR;
		}
			
		state = State.WAITING_TEXT;

		internalbb.limit(size);
		bb.flip();
		try {
			if (bb.remaining() <= internalbb.remaining()) {
				internalbb.put(bb);
			} else {
				var oldLimit = bb.limit();
				bb.limit(internalbb.remaining());
				internalbb.put(bb);
				bb.limit(oldLimit);
			}
		} finally {
			bb.compact();
		}
		if (internalbb.hasRemaining()) {
			return ProcessStatus.REFILL;
		}
		state = State.DONE;
		internalbb.flip();
		textValue = UTF8.decode(internalbb).toString();
		return ProcessStatus.DONE;

	}

	@Override
	public String get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return textValue;
	}

	@Override
	public void reset() {
		state = State.WAITING_SIZE;
		this.internalbb.clear();
		intReader.reset();
	}

}

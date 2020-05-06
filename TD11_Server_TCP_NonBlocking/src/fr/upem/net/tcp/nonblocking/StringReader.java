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
		// créer un reader qui prendre une lsite des readers avec une lambda a  appliqué avec le résultat
		// reflection 
		// il faut traiter les 3 cas ! 
		// lambda pour combiner les deux résultats
		// reader auquel ont donne une taille interne, traiter deux cas INT et STRING
		switch (state) {
			case WAITING_SIZE:
	
				var status = intReader.process(bb); // Which state returns intReader ?
				switch (status) {
					case REFILL:
						return ProcessStatus.REFILL;
					case DONE:
						break;
					default:
						throw new AssertionError();
					}
				this.size = intReader.get();
				if (this.size < 0 || this.size > MAX_BUFFER_SIZE) {
					this.state = State.ERROR;
					return ProcessStatus.ERROR;
				}
				this.state = State.WAITING_TEXT; // need to change state
				
			case WAITING_TEXT:
				var missingBytes = size - internalbb.position();
				bb.flip(); // --> read-mode
				if (bb.remaining() <= missingBytes) { // less bytes
					this.internalbb.put(bb);
				} else { // more bytes
					var oldLimit = bb.limit();
					bb.limit(missingBytes);
					this.internalbb.put(bb);
					bb.limit(oldLimit);
				}
				bb.compact(); // --> write-mode
				if (this.internalbb.position() < size) { // put bytes again
					return ProcessStatus.REFILL;
				}
				this.state = State.DONE;
				this.internalbb.flip(); // write-mode --> read-mode
				this.textValue = UTF8.decode(this.internalbb).toString();
				return ProcessStatus.DONE;

		default:
			throw new IllegalStateException("Unexpected state: " + state);
		}

		/*
		 * if (state == State.DONE || state == State.ERROR) { throw new
		 * IllegalStateException(); }
		 * 
		 * var processInt = intReader.process(bb); if (processInt != ProcessStatus.DONE)
		 * { return processInt; }
		 * 
		 * int size = intReader.get(); if (size > MAX_BUFFER_SIZE) { return
		 * ProcessStatus.ERROR; }
		 * 
		 * state = State.WAITING_TEXT;
		 * 
		 * internalbb.limit(size); bb.flip(); try { if (bb.remaining() <=
		 * internalbb.remaining()) { internalbb.put(bb); } else { var oldLimit =
		 * bb.limit(); bb.limit(internalbb.remaining()); internalbb.put(bb);
		 * bb.limit(oldLimit); } } finally { bb.compact(); } if
		 * (internalbb.hasRemaining()) { return ProcessStatus.REFILL; } state =
		 * State.DONE; internalbb.flip(); textValue =
		 * UTF8.decode(internalbb).toString(); return ProcessStatus.DONE;
		 */

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
		this.intReader.reset();
		this.internalbb.clear();
		this.textValue = null; // for garbage collector
	}

}

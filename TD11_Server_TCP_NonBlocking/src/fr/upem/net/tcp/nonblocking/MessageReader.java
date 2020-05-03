package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;

import fr.upem.net.tcp.nonblocking.MessageReader.Message;

public class MessageReader implements Reader<Message> {

	static class  Message {
		String login;
		String text;
	}

	private enum State {
		DONE, WAITING_LOGIN, WAITING_TEXT, ERROR
	};

	private State state = State.WAITING_LOGIN;
	private Message message = new Message();
	private StringReader stringReader;
	

	public MessageReader() {
		this.stringReader = new StringReader();
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		switch (state) {
		case WAITING_LOGIN:
			
			ProcessStatus resultProcessStateLogin = stringReader.process(bb);
			if(resultProcessStateLogin == ProcessStatus.DONE) {
				this.message.login = stringReader.get();
				this.stringReader.reset();
				this.state = State.WAITING_TEXT;
			}
			if (resultProcessStateLogin == ProcessStatus.REFILL) {
				return ProcessStatus.REFILL;
			} 
			if (resultProcessStateLogin == ProcessStatus.ERROR) {
				return ProcessStatus.ERROR;
			}
		case WAITING_TEXT:
			ProcessStatus resultProcessStateText = stringReader.process(bb);
			if(resultProcessStateText == ProcessStatus.DONE) {
				this.message.text = stringReader.get();
				this.stringReader.reset();
				this.state = State.DONE;
				return ProcessStatus.DONE;
			}
			if (resultProcessStateText == ProcessStatus.REFILL) {
				return ProcessStatus.REFILL;
			} 
			if (resultProcessStateText == ProcessStatus.ERROR) {
				return ProcessStatus.ERROR;
			}
		default:
			throw new IllegalStateException();
		}
	}

	@Override
	public Message get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return message;
	}

	@Override
	public void reset() {
		this.stringReader.reset();
		this.state = State.WAITING_LOGIN;

	}

}

package fr.upem.net.tcp;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ThreadDataCorrection {
	private SocketChannel sc;
	private long lastAction;
	private final Object lock = new Object(); // Garanti l'etat de l'objet

	/**
	 * Modify the client stream managed by this thread;
	 * 
	 * @param client client given
	 */
	public void setSocketChannel(SocketChannel sc) {
		synchronized (lock) {
			this.lastAction = System.currentTimeMillis();
			this.sc = sc;
		}
	}

	/**
	 * Indicates that the client is active at the time of the call to this method;
	 */
	public void tick() {
		synchronized (lock) {
			this.lastAction = System.currentTimeMillis();
		}
	}

	/**
	 * Disconnect the client if it is idle for more than timeout milliseconds
	 * 
	 * @param timeout timeout given
	 * @throws IOException
	 */
	public void closeIfTimeout(long timeout) throws IOException {

		try {
			synchronized (lock) {
				if (sc == null) {
					return;
				}

				if (System.currentTimeMillis() > lastAction + timeout) {
					this.close();
				}
			}
		} catch (IOException e) {
			//
		}

	}

	/**
	 * Disconnect the client
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		try {
			synchronized (lock) {
				if (sc == null) {
					return;
				}
				this.sc.close(); // call close with client attached to sc
				this.sc = null; // nothing client is treating
			}
		} catch (IOException e) {
			//
		}
		
		
	}

	public boolean isActive() {
		synchronized (lock) {
			return sc!= null;
		}
	}
}

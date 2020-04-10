package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class HTTPReader {

	private final Charset ASCII_CHARSET = Charset.forName("ASCII");
	private final SocketChannel sc; // sert pour lire la réponse
	private final ByteBuffer buff; // ByteBuffer de lecture associé
	public static final Logger logger = Logger.getLogger(HTTPReader.class.getName());
	public static final int SIZE_BUFFER = 1024;
	public static final int HEXA_BASE = 16;

	public HTTPReader(SocketChannel sc, ByteBuffer buff) {
		this.sc = sc;
		this.buff = buff;
	}

	/**
	 * CR = 'Carriage return' -> '\r' LF = 'Line feed' -> '\n'
	 * 
	 * @return The ASCII string terminated by CRLF without the CRLF
	 *         <p>
	 *         The method assume that buff is in write mode and leave it in
	 *         write-mode The method never reads from the socket as long as the
	 *         buffer is not empty
	 * @throws IOException HTTPException if the connection is closed before a line
	 *                     could be read
	 * 
	 */
	public String readLineCRLF() throws IOException {

		var ASCIIStr = new StringBuilder();
		this.buff.flip();
		var lastCR = false;
		var finished = false;

		while (true) {
			while (this.buff.hasRemaining()) {
				var currentChar = (char) this.buff.get();
				if (lastCR && currentChar == '\n') {
					finished = true;
					break;
				}
				if (currentChar == '\r') {
					lastCR = true;
				} else {
					lastCR = false;
				}
			}
			var tmpBuffer = this.buff.duplicate(); // Attention mal nommé, vue sur le buffer
			tmpBuffer.flip();
			ASCIIStr.append(StandardCharsets.US_ASCII.decode(tmpBuffer));
			if (finished) {
				break;
			}

			this.buff.clear();
			if (sc.read(this.buff) == -1) { // La connexion est fermée avant que une ligne puisse être lue
				throw new HTTPException("Server closed the connection before the end of line.");
			}
			this.buff.flip(); // read-mode
		}

		this.buff.compact(); // write mode
		ASCIIStr.setLength(ASCIIStr.length() - 2); // supprime le CRLF
		return ASCIIStr.toString();
	}

	/**
	 * @return The HTTPHeader object corresponding to the header read
	 * @throws IOException HTTPException if the connection is closed before a header
	 *                     could be read if the header is ill-formed
	 */

	public HTTPHeader readHeader() throws IOException {

		String firstLineResponse = readLineCRLF();
		Map<String, String> fieldsMap = new HashMap<String, String>();

		for (var line = this.readLineCRLF(); !line.isEmpty(); line = this.readLineCRLF()) {
			String[] token = line.split(": ", 2);
			if (token.length >= 2) {
				// fieldsMap.merge(token[0], token[1], String::concat); ?
				/*
				 * fieldsMap.merge(token[0], token[1], new BiFunction<String, String, String>()
				 * {
				 * 
				 * @Override public String apply(String s1, String s2) { return s1 + ";" + s2; }
				 * });
				 */
				fieldsMap.merge(token[0], token[1], (s1, s2) -> s1 + ";" + s2);
			}
		}
		return HTTPHeader.create(firstLineResponse, fieldsMap);

	}

	/**
	 * @param size
	 * @return a ByteBuffer in write-mode containing size bytes read on the socket
	 * @throws IOException HTTPException is the connection is closed before all
	 *                     bytes could be read
	 */
	public ByteBuffer readBytes(int size) throws IOException {
		var newBuffer = ByteBuffer.allocate(size);
		this.buff.flip();
		// newBuffer.put(this.buff.get());

		while (newBuffer.hasRemaining()) { // consomme le buffer
			if (this.buff.hasRemaining()) { // Si il reste des bytes dans la zone de travail
				newBuffer.put(this.buff.get());
			} else if (!readFully(sc, newBuffer)) { // Rempli tout l'espace qu'il reste dans le buffer
				throw new HTTPException(); // Le serveur à fermé la socket
			}
		}
		this.buff.compact();
		return newBuffer;
	}

	/**
	 * @return a ByteBuffer in write-mode containing a content read in chunks mode
	 * @throws IOException HTTPException if the connection is closed before the end
	 *                     of the chunks if chunks are ill-formed
	 */

	public ByteBuffer readChunks() throws IOException {

		int sizeChunk;
		var sb = new StringBuilder();

		while (true) {
			var line = this.readLineCRLF().trim();
			if (line.isEmpty()) {
				continue;
			}
			sizeChunk = Integer.parseInt(line, HEXA_BASE);
			if (sizeChunk == 0) {
				break;
			}
			var buff = ASCII_CHARSET.decode(this.readBytes(sizeChunk).flip());
			sb.append(buff.toString());
		}

		return ASCII_CHARSET.encode(sb.toString()).compact();
	}

	/**
	 * Rempli le buffer en lisant la socket
	 * 
	 * @param sc
	 * @param buff
	 * @return
	 * @throws IOException
	 */
	public static boolean readFully(SocketChannel sc, ByteBuffer buff) throws IOException {
		while (buff.hasRemaining()) {
			if (sc.read(buff) == -1) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) throws IOException {
		Charset charsetASCII = Charset.forName("ASCII");
		String request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";
		SocketChannel sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		sc.write(charsetASCII.encode(request));
		ByteBuffer bb = ByteBuffer.allocate(50);
		HTTPReader reader = new HTTPReader(sc, bb);
		System.out.println(reader.readLineCRLF());
		System.out.println(reader.readLineCRLF());
		System.out.println(reader.readLineCRLF());
		sc.close();

		bb = ByteBuffer.allocate(50);
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));

		reader = new HTTPReader(sc, bb);
		sc.write(charsetASCII.encode(request));
		System.out.println(reader.readHeader());
		sc.close();

		bb = ByteBuffer.allocate(50);
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		reader = new HTTPReader(sc, bb);
		sc.write(charsetASCII.encode(request));
		HTTPHeader header = reader.readHeader();
		System.out.println(header);
		ByteBuffer content = reader.readBytes(header.getContentLength());
		content.flip();
		System.out.println(header.getCharset().decode(content));
		sc.close();

		bb = ByteBuffer.allocate(50);
		request = "GET / HTTP/1.1\r\n" + "Host: www.u-pem.fr\r\n" + "\r\n";
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
		reader = new HTTPReader(sc, bb);
		sc.write(charsetASCII.encode(request));
		header = reader.readHeader();
		System.out.println(header);
		content = reader.readChunks();
		content.flip();
		System.out.println(header.getCharset().decode(content));
		sc.close();

	}
}

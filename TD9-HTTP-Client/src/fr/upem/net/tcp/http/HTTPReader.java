package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Logger;

public class HTTPReader {

	private final Charset ASCII_CHARSET = Charset.forName("ASCII");
	private final SocketChannel sc; // sert pour lire la réponse
	private final ByteBuffer buff; // ByteBuffer de lecture associé
	public static final Logger logger = Logger.getLogger(HTTPReader.class.getName());

	public HTTPReader(SocketChannel sc, ByteBuffer buff) {
		this.sc = sc;
		this.buff = buff;
	}
	
	
	public static boolean readFully(SocketChannel sc, ByteBuffer buff) throws IOException {
		while (buff.hasRemaining()) {
			if (sc.read(buff) == -1) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 
	 * 
	 * 
	 * @param buff
	 * @param sb
	 * @return
	 */
	public static boolean isCR(ByteBuffer buff, StringBuilder sb) {
		buff.flip();
		var crIsFound = false;

		while (buff.hasRemaining()) {
			var currentChar = (char) buff.get(); // on reduit notre zone de travail, on avance la position
			sb.append(currentChar); // On ajoute le caractère dans le sb
			if (currentChar == '\r') {
				crIsFound = true;
				// end of the line
			} else if (crIsFound && currentChar == '\n') {
				return true;
			} else {
				// Dans le cas ou un caractère après '\r' est different de '\n'
				crIsFound = false;
			}
		}
		return false;
	}

	/**
	 * CR = 'Carriage return' -> '\r' LF = 'Line feed' -> '\n'
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
		while (!isCR(this.buff, ASCIIStr)) {
			this.buff.clear();
			if (sc.read(this.buff) == -1) { // La connexion est fermée avant que une ligne puisse être lue
				throw new HTTPException("Server closed the connection before the end of line.");
			}
		}
		this.buff.compact();
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
			System.out.println(("token splited : " + token));
			if (token.length >= 2) {
				//fieldsMap.merge(token[0], token[1], String::concat); ?
				/*
				fieldsMap.merge(token[0], token[1], new BiFunction<String, String, String>() {
					@Override
					public String apply(String s1, String s2) {
						return s1 + ";" + s2;
					}
					
				});
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
		return null;
	}

	/**
	 * @return a ByteBuffer in write-mode containing a content read in chunks mode
	 * @throws IOException HTTPException if the connection is closed before the end
	 *                     of the chunks if chunks are ill-formed
	 */

	public ByteBuffer readChunks() throws IOException {
		return null;
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

		
		/*
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
		*/

	}
}

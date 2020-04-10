package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HTTPClient {

	private static final int SIZE_BUFFER = 1024;
	private static final int PORT = 80;
	private final static Charset ASCII_CHARSET = Charset.forName("ASCII");
	private final static String HTML_TYPE  = "text/html";

	private String adressServer;

	private String ressource;

	public HTTPClient(String adressServer, String ressource) {
		this.adressServer = adressServer;
		this.ressource = ressource;
	}

	public String getResponse() throws IOException {
		System.out.println(this.adressServer);
		String request = "GET " + this.ressource + " HTTP/1.1\r\n" + "Host: " + this.adressServer + "\r\n" + "\r\n";
		System.out.println(request);
		var  buffer = ByteBuffer.allocate(SIZE_BUFFER);
		
		try(var sc = SocketChannel.open(new InetSocketAddress(this.adressServer, PORT));) {
			sc.write(ASCII_CHARSET.encode(request));
			var httpReader = new HTTPReader(sc, buffer);
			var header = httpReader.readHeader();
			var contentLength = header.getContentLength();
			System.out.println("Code response : " + header.getCode());
			
			var codeResponse = header.getCode();
			if(codeResponse == 301 || codeResponse == 302) {
				var redirectLocation = header.getFields().get("location");
				this.adressServer = redirectLocation;
				return getResponse();
			}
			
			
			var contentType  = header.getContentType();
			if (!HTML_TYPE.equals(contentType)) {
				throw new IllegalArgumentException("No HTML content");
			}
			
			var charset = header.getCharset(); // Recupérer le charset du serveur dans le header
			if(charset == null) {
				charset =  StandardCharsets.UTF_8;
			}
			
			if(header.isChunkedTransfer()) {
				return charset.decode(httpReader.readChunks().flip()).toString(); // Chaine quand c'est chunk (body)
			}
			
			return charset.decode(httpReader.readBytes(contentLength).flip()).toString(); // content
			
		} 
	
	}

	private static void usage() {
		System.out.println("Usage : HTTPClient address_server resource");
	}

	public static void main(String[] args) throws IOException {
		
		if(args.length != 2) {
			usage();
			return;
		}
			
		var client = new HTTPClient(args[0], args[1]); // \/ -> / pour Git bash (win) 
		
		client.getResponse();
	}

}

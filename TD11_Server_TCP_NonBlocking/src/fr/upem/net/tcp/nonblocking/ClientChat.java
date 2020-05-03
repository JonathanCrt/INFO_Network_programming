package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 
 * @author judicael koffi
 *
 */
public class ClientChat {

  private static void sendListContent(SocketChannel sc, int size) throws IOException {
    var values = IntStream.range(0, size).boxed().collect(Collectors.toList());
    var buffer = ByteBuffer.allocate(Integer.BYTES * size);
    for (int v : values) {
      buffer.putInt(v);
    }
    buffer.flip();
    sc.write(buffer);
    buffer.clear();
  }


  private static void sendMessage(SocketChannel sc, String msg) throws IOException {
    var charset = StandardCharsets.UTF_8;
    var bb = charset.encode(msg);
    var size = bb.limit();
    var buffer = ByteBuffer.allocate(Integer.BYTES + size);
    buffer.putInt(size);
    buffer.put(bb);
    buffer.flip();
    sc.write(buffer);
  }



  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("Usage: ClientChat addr port size");
      return;
    }

    // var size = Integer.valueOf(args[2]);
    InetSocketAddress server = new InetSocketAddress(args[0], Integer.valueOf(args[1]));

    try (SocketChannel sc = SocketChannel.open(server)) {
      sendMessage(sc, args[2]);
      System.out.println("Everything seems ok");
    }
  }
}
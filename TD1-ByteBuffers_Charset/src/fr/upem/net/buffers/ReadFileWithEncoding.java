package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ReadFileWithEncoding {

    private static void usage() {
        System.out.println("Usage: ReadFileWithEncoding charset filename");
    }

    private static String stringFromFile(Charset cs, Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ)) {
            var buff = ByteBuffer.allocate((int) channel.size());
            while (buff.hasRemaining()) { // return true if we can access
                if (channel.read(buff) == -1)
                    break;
            }
            return cs.decode(buff.flip()).toString();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        Charset cs = Charset.forName(args[0]);
        Path path = Paths.get(args[1]);
        System.out.print(stringFromFile(cs, path));
    }


}

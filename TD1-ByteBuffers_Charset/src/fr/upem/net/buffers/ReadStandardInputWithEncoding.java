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

public class ReadStandardInputWithEncoding {

    private static final int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage: ReadStandardInputWithEncoding charset");
    }


    /**
     * Read all the bytes from the standard input and return the corresponding string.
     *
     * @param cs
     * @return
     * @throws IOException
     */
    private static String stringFromStandardInput(Charset cs) throws IOException {
        try (var channel = Channels.newChannel(System.in)) {
            ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
            while (channel.read(buff) != -1) { // Tant que la lecture du fileChannel ne renvoie pas -1.
                if (!buff.hasRemaining()) { // Si tous les octets ont etes lus
                    int newCapacity = buff.capacity() * 2; // On double la capacite
                    buff = ByteBuffer.allocate(newCapacity).put(buff.flip()); // Et on alloue un nouveau buffer avec
                    // une nouvelle capacite et on ecrit les octets du buffers relus
                }

            }
            return cs.decode(buff.flip()).toString();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        Charset cs = Charset.forName(args[0]);
        System.out.print(stringFromStandardInput(cs));


    }


}

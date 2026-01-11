import org.agrona.concurrent.UnsafeBuffer;

import static example.SomeMessageExtensionsKt.*;

void main() {
    var message = randomSomeMessage();
    var buffer = new UnsafeBuffer(new byte[1024]);
    var bytesWritten = encodeSomeMessage(buffer, message);

    System.out.println(message + " -> byte[" + bytesWritten + "]");

    var newMessage = newSomeMessage(false, '\u0000', (byte) 0, (short) 0, 0, 0L, 0.0f, 0.0);
    var bytesRead = decodeSomeMessage(buffer, newMessage);

    System.out.println("byte[" + bytesRead + "] -> " + newMessage);

    System.out.println("\nRandom message:");
    var randomMsg = randomSomeMessage();
    System.out.println(randomMsg);
    printSbeLayout(randomMsg);
}

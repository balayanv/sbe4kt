package example;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * Java example demonstrating interop with Kotlin-generated SBE classes.
 */
public class Example {

    static void main(String[] args) {
        var fw = new MsgFW();

        for (int i = 0; i < 100; i++) {
            var random = MsgExtensionsKt.randomMsg();
            var target = MsgExtensionsKt.newMsg();

            fw.encode(random);
            fw.decode(target);

            assert random.equals(target) : "Mismatch at iteration " + i;
        }

        MsgExtensionsKt.printSbeLayout(MsgExtensionsKt.randomMsg(), false);
    }
}

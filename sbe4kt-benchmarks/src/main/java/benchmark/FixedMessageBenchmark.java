package benchmark;

import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.nio.ByteBuffer;

public class FixedMessageBenchmark {

   @State(Scope.Benchmark)
   public static class MyState {
      final int bufferIndex = 0;

      final sbe4kt.MessageHeaderEncoder sbeHeaderEncoder = new sbe4kt.MessageHeaderEncoder();
      final sbe4kt.MessageHeaderDecoder sbeHeaderDecoder = new sbe4kt.MessageHeaderDecoder();
      final sbe4kt.FixedMessageEncoder sbeEncoder = new sbe4kt.FixedMessageEncoder();
      final sbe4kt.FixedMessageDecoder sbeDecoder = new sbe4kt.FixedMessageDecoder();
      final UnsafeBuffer sbeEncodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
      final UnsafeBuffer sbeDecodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

      {
         encodeSbe(sbeHeaderEncoder, sbeEncoder, sbeDecodeBuffer, bufferIndex);
      }
   }

   @Benchmark
   @BenchmarkMode(Mode.AverageTime)
   public int testSbeEncode(final MyState state) {
      encodeSbe(state.sbeHeaderEncoder, state.sbeEncoder, state.sbeEncodeBuffer, state.bufferIndex);
      return state.sbeEncoder.encodedLength();
   }

   @Benchmark
   @BenchmarkMode(Mode.AverageTime)
   public long testSbeDecode(final MyState state) {
      return decodeSbe(state.sbeHeaderDecoder, state.sbeDecoder, state.sbeDecodeBuffer, state.bufferIndex);
   }

   static void encodeSbe(
      final sbe4kt.MessageHeaderEncoder messageHeader,
      final sbe4kt.FixedMessageEncoder msg,
      final UnsafeBuffer buffer,
      final int bufferIndex
   ) {
      msg.wrapAndApplyHeader(buffer, bufferIndex, messageHeader);

      msg.counters().c0(100).c1(200).c2(300).c3(400);
      msg.timestamp().seconds(1709913600L).nanoseconds(500_000_000);
      msg.price().mantissa(123456789L).exponent((byte) -4);

      msg.fieldLong1(1_000_000_000L);
      msg.fieldLong2(-999_999_999L);
      msg.fieldDouble(2.718281828);

      msg.coordinates().latitude(51.5074f).longitude(-0.1278f);
      msg.identifier().code(987654).region((short) 42).source((byte) 1);

      msg.fieldLong3(777_777_777L);
      msg.dimensions().length((short) 1500).width((short) 800).height((short) 600);

      msg.fieldInt1(-50_000);
      msg.fieldInt2(100_000);
      msg.fieldFloat(3.14f);
      msg.fieldShort1((short) 1024);
      msg.fieldShort2((short) -512);
      msg.fieldByte1((byte) 42);
      msg.fieldByte2((byte) -7);
      msg.fieldChar((byte) 'Z');
      msg.status((byte) 1);
   }

   static long decodeSbe(
      final sbe4kt.MessageHeaderDecoder messageHeader,
      final sbe4kt.FixedMessageDecoder msg,
      final UnsafeBuffer buffer,
      final int bufferIndex
   ) {
      messageHeader.wrap(buffer, bufferIndex);
      msg.wrap(
         buffer, bufferIndex + messageHeader.encodedLength(),
         messageHeader.blockLength(), messageHeader.version()
      );

      long hash = 0;

      final sbe4kt.CountersDecoder counters = msg.counters();
      hash += counters.c0();
      hash += counters.c1();
      hash += counters.c2();
      hash += counters.c3();

      final sbe4kt.HiResTimestampDecoder timestamp = msg.timestamp();
      hash += timestamp.seconds();
      hash += timestamp.nanoseconds();

      final sbe4kt.DecimalPriceDecoder price = msg.price();
      hash += price.mantissa();
      hash += price.exponent();

      hash += msg.fieldLong1();
      hash += msg.fieldLong2();
      hash += Double.doubleToRawLongBits(msg.fieldDouble());

      final sbe4kt.CoordinatesDecoder coordinates = msg.coordinates();
      hash += Float.floatToRawIntBits(coordinates.latitude());
      hash += Float.floatToRawIntBits(coordinates.longitude());

      final sbe4kt.IdentifierDecoder identifier = msg.identifier();
      hash += identifier.code();
      hash += identifier.region();
      hash += identifier.source();

      hash += msg.fieldLong3();

      final sbe4kt.DimensionsDecoder dimensions = msg.dimensions();
      hash += dimensions.length();
      hash += dimensions.width();
      hash += dimensions.height();

      hash += msg.fieldInt1();
      hash += msg.fieldInt2();
      hash += Float.floatToRawIntBits(msg.fieldFloat());
      hash += msg.fieldShort1();
      hash += msg.fieldShort2();
      hash += msg.fieldByte1();
      hash += msg.fieldByte2();
      hash += msg.fieldChar();
      hash += msg.status();

      return hash;
   }
}

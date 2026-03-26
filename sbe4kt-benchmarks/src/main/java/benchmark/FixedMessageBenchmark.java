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

      // Pure SBE flyweights
      final sbe4kt.MessageHeaderEncoder sbeHeaderEncoder = new sbe4kt.MessageHeaderEncoder();
      final sbe4kt.MessageHeaderDecoder sbeHeaderDecoder = new sbe4kt.MessageHeaderDecoder();
      final sbe4kt.FixedMessageEncoder sbeEncoder = new sbe4kt.FixedMessageEncoder();
      final sbe4kt.FixedMessageDecoder sbeDecoder = new sbe4kt.FixedMessageDecoder();
      final UnsafeBuffer sbeEncodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
      final UnsafeBuffer sbeDecodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));


      final UnsafeBuffer sbe4ktEncodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
      final UnsafeBuffer sbe4ktDecodeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
      final FixedMessageFW fwE = new FixedMessageFW(sbe4ktEncodeBuffer, bufferIndex);
      final FixedMessageFW fwD = new FixedMessageFW(sbe4ktDecodeBuffer, bufferIndex);

      {
         encodeSbe(sbeHeaderEncoder, sbeEncoder, sbeDecodeBuffer, bufferIndex);
         encodeSbe4kt(fwE);
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

   @Benchmark
   @BenchmarkMode(Mode.AverageTime)
   public int testSbe4ktEncode(final MyState state) {
      encodeSbe4kt(state.fwE);
      return FixedMessageCodec.SIZE;
   }

   @Benchmark
   @BenchmarkMode(Mode.AverageTime)
   public long testSbe4ktDecode(final MyState state) {
      return decodeSbe4kt(state.fwD);
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

   static void encodeSbe4kt(final FixedMessageFW fw) {
      fw.writeHeader();

      fw.counters().writeC0(100).writeC1(200).writeC2(300).writeC3(400);
      fw.timestamp().writeSeconds(1709913600L).writeNanoseconds(500_000_000);
      fw.price().writeMantissa(123456789L).writeExponent((byte) -4);

      fw.writeFieldLong1(1_000_000_000L);
      fw.writeFieldLong2(-999_999_999L);
      fw.writeFieldDouble(2.718281828);

      fw.coordinates().writeLatitude(51.5074f).writeLongitude(-0.1278f);
      fw.identifier().writeCode(987654).writeRegion((short) 42).writeSource((byte) 1);

      fw.writeFieldLong3(777_777_777L);
      fw.dimensions().writeLength((short) 1500).writeWidth((short) 800).writeHeight((short) 600);

      fw.writeFieldInt1(-50_000);
      fw.writeFieldInt2(100_000);
      fw.writeFieldFloat(3.14f);
      fw.writeFieldShort1((short) 1024);
      fw.writeFieldShort2((short) -512);
      fw.writeFieldByte1((byte) 42);
      fw.writeFieldByte2((byte) -7);
      fw.writeFieldChar('Z');
      fw.writeStatus(Status.ACTIVE);
   }

   static long decodeSbe4kt(final FixedMessageFW fw) {
      long hash = 0;

      hash += fw.readBlockLength();
      hash += fw.readTemplateId();
      hash += fw.readSchemaId();
      hash += fw.readVersion();

      final CountersFW counters = fw.counters();
      hash += counters.readC0();
      hash += counters.readC1();
      hash += counters.readC2();
      hash += counters.readC3();

      final HiResTimestampFW timestamp = fw.timestamp();
      hash += timestamp.readSeconds();
      hash += timestamp.readNanoseconds();

      final DecimalPriceFW price = fw.price();
      hash += price.readMantissa();
      hash += price.readExponent();

      hash += fw.readFieldLong1();
      hash += fw.readFieldLong2();
      hash += Double.doubleToRawLongBits(fw.readFieldDouble());

      final CoordinatesFW coordinates = fw.coordinates();
      hash += Float.floatToRawIntBits(coordinates.readLatitude());
      hash += Float.floatToRawIntBits(coordinates.readLongitude());

      final IdentifierFW identifier = fw.identifier();
      hash += identifier.readCode();
      hash += identifier.readRegion();
      hash += identifier.readSource();

      hash += fw.readFieldLong3();

      final DimensionsFW dimensions = fw.dimensions();
      hash += dimensions.readLength();
      hash += dimensions.readWidth();
      hash += dimensions.readHeight();

      hash += fw.readFieldInt1();
      hash += fw.readFieldInt2();
      hash += Float.floatToRawIntBits(fw.readFieldFloat());
      hash += fw.readFieldShort1();
      hash += fw.readFieldShort2();
      hash += fw.readFieldByte1();
      hash += fw.readFieldByte2();
      hash += fw.readFieldChar();
      hash += fw.readStatus().ordinal();

      return hash;
   }

   public static void main(final String[] args) {
      for (int i = 0; i < 10; i++) {
         perfTestSbeEncode(i);
         perfTestSbeDecode(i);
         perfTestSbe4ktEncode(i);
         perfTestSbe4ktDecode(i);
      }
   }

   private static void perfTestSbeEncode(final int runNumber) {
      final int reps = 10_000_000;
      final MyState state = new MyState();
      final FixedMessageBenchmark benchmark = new FixedMessageBenchmark();

      final long start = System.nanoTime();
      for (int i = 0; i < reps; i++) {
         benchmark.testSbeEncode(state);
      }
      final long totalDuration = System.nanoTime() - start;

      System.out.printf(
         "%d - %d(ns) average duration for %s.testSbeEncode()%n",
         runNumber, totalDuration / reps, benchmark.getClass().getName()
      );
   }

   private static void perfTestSbeDecode(final int runNumber) {
      final int reps = 10_000_000;
      final MyState state = new MyState();
      final FixedMessageBenchmark benchmark = new FixedMessageBenchmark();

      final long start = System.nanoTime();
      for (int i = 0; i < reps; i++) {
         benchmark.testSbeDecode(state);
      }
      final long totalDuration = System.nanoTime() - start;

      System.out.printf(
         "%d - %d(ns) average duration for %s.testSbeDecode()%n",
         runNumber, totalDuration / reps, benchmark.getClass().getName()
      );
   }

   private static void perfTestSbe4ktEncode(final int runNumber) {
      final int reps = 10_000_000;
      final MyState state = new MyState();
      final FixedMessageBenchmark benchmark = new FixedMessageBenchmark();

      final long start = System.nanoTime();
      for (int i = 0; i < reps; i++) {
         benchmark.testSbe4ktEncode(state);
      }
      final long totalDuration = System.nanoTime() - start;

      System.out.printf(
         "%d - %d(ns) average duration for %s.testSbe4ktEncode()%n",
         runNumber, totalDuration / reps, benchmark.getClass().getName()
      );
   }

   private static void perfTestSbe4ktDecode(final int runNumber) {
      final int reps = 10_000_000;
      final MyState state = new MyState();
      final FixedMessageBenchmark benchmark = new FixedMessageBenchmark();

      final long start = System.nanoTime();
      for (int i = 0; i < reps; i++) {
         benchmark.testSbe4ktDecode(state);
      }
      final long totalDuration = System.nanoTime() - start;

      System.out.printf(
         "%d - %d(ns) average duration for %s.testSbe4ktDecode()%n",
         runNumber, totalDuration / reps, benchmark.getClass().getName()
      );
   }
}

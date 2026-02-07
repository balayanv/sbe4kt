import example.sbe.MessageHeaderEncoder;
import example.sbe.NewOrderSingleEncoder;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

void main() {
	// 1. Setup the buffer
	var byteBuffer = ByteBuffer.allocateDirect(128);
	var buffer = new UnsafeBuffer(byteBuffer);

	// 2. Initialize Encoder and Header
	var encoder = new NewOrderSingleEncoder();
	var headerEncoder = new MessageHeaderEncoder();

	// 3. Write the Header (Standard SBE requirement)
	headerEncoder.wrap(buffer, 0).blockLength(encoder.sbeBlockLength()).templateId(encoder.sbeTemplateId()).schemaId(encoder.sbeSchemaId()).version(encoder.sbeSchemaVersion());

	// 4. Encode the Message Body
	// Note: The offset is the length of the header we just wrote
	encoder.wrap(buffer, headerEncoder.encodedLength());

	//		encoder.orderId(1001L).price(99.50);

	// 5. SET THE FLAGS (The Bitset Logic)
	// Chain the calls. 'clear()' ensures we start with 00000000.
	encoder.flags().clear().isGTC(true)    // Sets Bit 0 to 1
		.isFOK(false)   // Sets Bit 1 to 0
		.isAlgo(true)   // Sets Bit 2 to 1
		.isHidden(true);// Sets Bit 3 to 1

	// Result on wire for flags: Binary 00001101 = Decimal 13

	IO.println("Message encoded successfully.");
}
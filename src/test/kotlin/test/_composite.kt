package test.composite

import org.agrona.concurrent.UnsafeBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sbe4kt.Message
import sbe4kt.Metadata
import test.enums.Color


@Message
data class Point(
   var color: Color,

   @Metadata(isComposite = true)
   var position : XY
)

data class XY(
   var x: Int,
   var y: Int,
)

// More complex example with multiple composite fields
@Message(id = 10)
data class Rectangle(
   @Metadata(isComposite = true)
   var topLeft: XY,

   @Metadata(isComposite = true)
   var bottomRight: XY,

   var filled: Boolean
)

// Example with nested composite (composite containing another type)
@Message(id = 11)
data class ComplexMessage(
   var id: Long,

   @Metadata(isComposite = true)
   var coordinates: Coordinates3D,

   var timestamp: Long
)

data class Coordinates3D(
   var x: Int,
   var y: Int,
   var z: Int
)


class CompositeTest {

   @Test
   fun testSimpleComposite() {
      val point = Point(
         color = Color.RED,
         position = XY(x = 100, y = 200)
      )

      // Test encoding and decoding
      val buffer = UnsafeBuffer(ByteArray(1024))
      val encodedSize = encodePoint(point, buffer)

      val decodedPoint = newPoint()
      val decodedSize = decodePoint(decodedPoint, buffer)

      assertEquals(encodedSize, decodedSize)
      assertEquals(point.color, decodedPoint.color)
      assertEquals(point.position.x, decodedPoint.position.x)
      assertEquals(point.position.y, decodedPoint.position.y)
   }

   @Test
   fun testMultipleComposites() {
      val rect = Rectangle(
         topLeft = XY(x = 10, y = 20),
         bottomRight = XY(x = 100, y = 200),
         filled = true
      )

      val buffer = UnsafeBuffer(ByteArray(1024))
      val encodedSize = encodeRectangle(rect, buffer)

      val decodedRect = newRectangle()
      val decodedSize = decodeRectangle(decodedRect, buffer)

      assertEquals(encodedSize, decodedSize)
      assertEquals(rect.topLeft.x, decodedRect.topLeft.x)
      assertEquals(rect.topLeft.y, decodedRect.topLeft.y)
      assertEquals(rect.bottomRight.x, decodedRect.bottomRight.x)
      assertEquals(rect.bottomRight.y, decodedRect.bottomRight.y)
      assertEquals(rect.filled, decodedRect.filled)
   }

   @Test
   fun testCompositeWith3DCoordinates() {
      val msg = ComplexMessage(
         id = 12345L,
         coordinates = Coordinates3D(x = 1, y = 2, z = 3),
         timestamp = System.currentTimeMillis()
      )

      msg.printSbeLayout()

      val buffer = UnsafeBuffer(ByteArray(1024))
      val encodedSize = encodeComplexMessage(msg, buffer)

      val decodedMsg = newComplexMessage()
      val decodedSize = decodeComplexMessage(decodedMsg, buffer)

      assertEquals(encodedSize, decodedSize)
      assertEquals(msg.id, decodedMsg.id)
      assertEquals(msg.coordinates.x, decodedMsg.coordinates.x)
      assertEquals(msg.coordinates.y, decodedMsg.coordinates.y)
      assertEquals(msg.coordinates.z, decodedMsg.coordinates.z)
      assertEquals(msg.timestamp, decodedMsg.timestamp)
   }

   @Test
   fun testCompositeFlyweight() {
      val pointRW = PointRW()

      // Set values using flyweight
      pointRW.color(Color.GREEN)
      pointRW.position().x(50)
      pointRW.position().y(75)

      // Read back values
      assertEquals(Color.GREEN, pointRW.color())
      val pos = pointRW.position()
      assertEquals(50, pos.x())
      assertEquals(75, pos.y())

      // Test encoding an instance
      val point = Point(
         color = Color.BLUE,
         position = XY(x = 300, y = 400)
      )
      pointRW.encode(point)

      assertEquals(Color.BLUE, pointRW.color())
      val newPos = pointRW.position()
      assertEquals(300, newPos.x())
      assertEquals(400, newPos.y())
   }

   @Test
   fun testPrintLayout() {
      val point = Point(
         color = Color.RED,
         position = XY(x = 123, y = 456)
      )
      point.printSbeLayout()

      val rect = Rectangle(
         topLeft = XY(x = 0, y = 0),
         bottomRight = XY(x = 800, y = 600),
         filled = false
      )
      rect.printSbeLayout()
   }
}


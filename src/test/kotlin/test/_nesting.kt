//package test.nesting
//
//import org.junit.jupiter.api.Test
//import sbe4kt.Message
//import test.composite.printSbeLayout
//import test.composite.randomPoint
//import test.enums.Color
//
//
//@Message
//data class Point(
//    var color: Color,
//    var position : XY
//)
//
//@Message
//data class XY(
//   var x: Int,
//   var y: Int,
//)
//
//
//class NestingTest {
//
//   @Test
//   fun testNestedMessages() {
//      randomPoint().printSbeLayout()
//   }
//}

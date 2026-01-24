package example

import misc.log
import sbe4kt.META
import sbe4kt.SBE
import sbe4kt.lib.*

object Symbol : CharSet("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")

@SBE
data class Trade(
   var id: TimeId,
   @META(tags = ["PublishedToExhaust", "DeserializedOnMDS", "PublishedViaWS"])
   var timer: MicroTrace,
   var symbol: SymbolStr64,
   var price: FPD,
   var amount: FPD,
)

fun Trade.recordPublishedToExhaust(time: TimeId) = apply { timer = timer.set1(time.us() - id.us()) }
fun Trade.recordDeserializedOnMDS(time: TimeId) = apply { timer = timer.set2(time.us() - id.us()) }
fun Trade.recordPublishedViaWS(time: TimeId) = apply { timer = timer.set3(time.us() - id.us()) }

fun Trade.getTimePublishedToExhaust() = id.add(timer.get1())
fun Trade.getTimeDeserializedOnMDS() = getTimePublishedToExhaust().add(timer.get2())
fun Trade.getTimePublishedViaWS() = getTimeDeserializedOnMDS().add(timer.get3())

fun main() {
   val time = TimeId.now().tag(77)

   val trade = newTrade(
      id = time,
      timer = MicroTrace(),
      symbol = SymbolStr64.from("BTC_USD"),
      price = FPD.from(78000),
      amount = FPD.from(1),
   )

   "-----------------------------------------------------------------------------------------------".log()
   "trade event was created                   : $trade".log()
   "trade event leaving the exhaust service   : ${trade.recordPublishedToExhaust(time.add(11.us))}".log()
   "trade event deserialized on MDS           : ${trade.recordDeserializedOnMDS(time.add(22.us))}".log()
   "trade event published via WS              : ${trade.recordPublishedViaWS(time.add(33.us))}".log()

   "-----------------------------------------------------------------------------------------------".log()
   "checking if recorded packed time diffs unpack into proper timestamps".log()
   "getTimePublishedToExhaust  : ${trade.getTimePublishedToExhaust()}".log()
   "getTimeDeserializedOnMDS   : ${trade.getTimeDeserializedOnMDS()}".log()
   "getTimePublishedViaWS      : ${trade.getTimePublishedViaWS()}".log()

   "-----------------------------------------------------------------------------------------------".log()
   trade.printSbeLayout()
}
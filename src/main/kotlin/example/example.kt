package example

import misc.log
import sbe4kt.META
import sbe4kt.SBE
import sbe4kt.lib.*

object Symbol : CharSet("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")

@SBE
data class Trade(
   var id: TimeId,
   @META(tags = ["id", "PublishedToExhaust", "DeserializedOnMDS", "PublishedViaWS", "MarkedConsumed"])
   var tags: MicroTag4,
   var symbol: SymbolStr64,
   var price: FPD,
   var amount: FPD,
)

fun main() {
   val time = TimeId.now().tag(77)

   val trade = newTrade(
      id = time,
      tags = MicroTag4(),
      symbol = SymbolStr64.from("BTC_USD"),
      price = FPD.from(78000),
      amount = FPD.from(1),
   )

   "SIMULATING A TRADE EVENT GOING TROUGH VARIOUS PROCESSES IN A SYSTEM".log()
   "trade event was created                   : $trade".log()
   "trade event leaving the exhaust service   : ${trade.recordPublishedToExhaust(time.add(10.us))}".log()
   "trade event deserialized on MDS           : ${trade.recordDeserializedOnMDS(time.add(20.us))}".log()
   "trade event published via WS              : ${trade.recordPublishedViaWS(time.add(30.us))}".log()
   "trade event was marked consumed           : ${trade.recordMarkedConsumed(time.add(50.us))}".log()

   "CHECKING IF RECORDED PACKED TIME DIFFS UNPACK INTO PROPER TIMESTAMPS".log()
   "getTimePublishedToExhaust  : ${trade.getTimePublishedToExhaust()}".log()
   "getTimeDeserializedOnMDS   : ${trade.getTimeDeserializedOnMDS()}".log()
   "getTimePublishedViaWS      : ${trade.getTimePublishedViaWS()}".log()
   "getTimeMarkedConsumed      : ${trade.getTimeMarkedConsumed()}".log()

   trade.printSbeLayout()
}
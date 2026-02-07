package sandbox

import io.cucumber.java.en.*

class CalculatorSteps {

   private val numbers = mutableListOf<Int>()
   private var result = 0

   @Given("I have the number {int}")
   fun iHaveTheNumber(number: Int) {
      numbers.add(number)
   }

   @When("I add them together")
   fun iAddThem() {
      result = numbers.sum()
   }

   @When("I subtract them")
   fun iSubtractThem() {
      result = numbers[0] - numbers[1]
   }

   @When("I multiply them")
   fun iMultiplyThem() {
      result = numbers[0] * numbers[1]
   }

   @Then("the result should be {int}")
   fun theResultShouldBe(expected: Int) {
      assert(result == expected) { "Expected $expected but got $result" }
   }
}

Feature: Calculator

  Scenario: Adding two numbers
    Given I have the number 5
    And I have the number 3
    When I add them together
    Then the result should be 8

  Scenario: Subtracting two numbers
    Given I have the number 10
    And I have the number 4
    When I subtract them
    Then the result should be 6

  Scenario: Multiplying two numbers
    Given I have the number 6
    And I have the number 7
    When I multiply them
    Then the result should be 42

  Scenario Outline: Multiple additions
    Given I have the number <a>
    And I have the number <b>
    When I add them together
    Then the result should be <sum>

    Examples:
      | a   | b   | sum |
      | 0   | 0   | 0   |
      | 1   | 0   | 1   |
      | 100 | 200 | 300 |

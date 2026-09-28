@smoke @table
Feature: Tables are read by column header, not by cell position

  A test that says getColumnValues("Due") keeps working when somebody inserts a column upstream.
  One that says td:nth-child(4) does not, and the failure it produces says nothing about why.

  Background:
    Given I open the "Data Tables" example

  Scenario: The table exposes its own structure
    Then the page heading contains "Data Tables"
    And the table has 4 rows
    And the table columns include "Last Name"
    And the table columns include "Due"

  Scenario: A whole column can be read by its header
    Then the "Last Name" column contains "Conway"
    And the "Last Name" column contains "Smith"
    And the "Due" column contains "$100.00"
    And the "Last Name" column does not contain "Nobody"

  Scenario: A single cell is addressed by row and header
    Then row 1 of the table has "Last Name" = "Smith"
    And row 1 of the table has "First Name" = "John"
    And row 1 of the table has "Due" = "$50.00"

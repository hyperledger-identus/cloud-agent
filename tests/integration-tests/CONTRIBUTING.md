# Contributing to integration tests

Please, read this guide before contributing to integration tests.

## Adding new scenario

To add new scenario, follow these steps:

1. Create a new feature file in the `src/test/resources/features/new_feature` folder.
2. Implement steps definitions in the `src/test/kotlin/features/new_feature` folder.
3. Test the scenario properly, make sure it works for different configurations.
4. Submit a pull request with the new scenario.

When creating a new scenario, please, follow the coding rules guidelines represented in the next section.

## Coding rules

### Always use constants for test data

Do not use hardcoded values in the tests. Always use constants instead.
Instead of adding `Name` and `Surname` to the test, add `NAME` and `SURNAME` constants to the `common.TestConstants` class.

This approach has the following advantages:

* It is easier to find and change the test data.
* It is easier to understand the test logic.
* It is easier to reuse the test data in other tests.

### Naming conventions

* `camelCase` is used for naming variables, methods, and classes.
* `UPPER_SNAKE_CASE` is used for naming constants.
* `snakecase` is used for naming files and folders.

### Use Cucumber best practices

Use [Cucumber best practices](https://www.browserstack.com/guide/cucumber-best-practices-for-testing) when writing Gherkin scenarios.

### Waiting must always be asynchronous

Do not use `Thread.sleep` to wait for the result of an asynchronous operation.
Use [Awaitility](http://www.awaitility.org/) instead. The `wait` method is implemented in the `common.Utils` object.

Wrong usage example:

```kotlin
for(i in 1..10) {
    val didEvent =
        ListenToEvents.`as`(actor).didEvents.lastOrNull {
            it.data.did == actor.recall<String>("shortFormDid")
        }
    if (didEvent != null && didEvent.data.status == "PUBLISHED") {
        break
    }
    Thread.sleep(5000)
}
```

Correct usage example:

```kotlin
wait(
    {
        val didEvent =
            ListenToEvents.`as`(actor).didEvents.lastOrNull {
                it.data.did == actor.recall<String>("shortFormDid")
            }
        didEvent != null && didEvent.data.status == "PUBLISHED"
    },
    "ERROR: DID was not published to ledger!",
    timeout = TestConstants.DID_UPDATE_PUBLISH_MAX_WAIT_5_MIN
)
```

### Sharing context between steps

Actor can use `remember` and `recall` methods to share context between steps.
The `remember` method stores the value in the actor's memory,
and the `recall` method retrieves the value from the actor's memory.

Usage example:

```kotlin
// Remember connection in some step
val connection = SerenityRest.lastResponse().get<Connection>()
inviter.remember("connection", connection)
//...
// Recalls connection in some other step
val connection = inviter.recall<Connection>("connection")
```

### Independent scenarios

Scenarios must be independent of each other. Do not use the state from one scenario in another scenario.

It allows you to run scenarios in parallel and avoid unexpected test failures.

## Goal

In the `VdrService.scala`, we have a service config which accepts blockfrost config.
The config current uses hardcoded values. We should expose these configuration to the standard `application.conf`.
These configurations are read and parsed into the `AppConfig.scala`.

The configuration should accept `blockfrostApiKey` and optional`privateNetwork` config.
The config should accept one of those config and they are mutually exclusive.
The `AppConfig` should validate the config according to the rule.

There should also be the tests to validate the configuration validation.
You can find the tests in the `AppConfigSpec.scala`.




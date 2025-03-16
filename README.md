# ConfigProvider: Java Library for processing Config Files in JSON and YAML

ConfigProvider can be configured for JSON, by default. It can be
configured to looks for YAML via a function call.

Default Files:

- src/main/resources/config
  - default.json
  - ${APP_ENV}.json

# Configure for YAML

```java
    ConfigProvider.setConfigType(ConfigProvider.ConfigTypes.YAML);
    String appEnv = ConfigProverer.getInstance().getStringProperty("APP_ENV");
```
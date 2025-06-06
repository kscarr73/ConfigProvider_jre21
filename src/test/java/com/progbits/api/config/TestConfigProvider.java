package com.progbits.api.config;

import org.testng.annotations.Test;

public class TestConfigProvider {
    private static ConfigProvider config = ConfigProvider.getInstance();

    @Test
    public void testConfigProvider() {
        assert config.getStringProperty("APP_ENV") != null;
        assert "this".equals(config.getStringProperty("testing")) : "Default File Failed";
        assert "DEV".equals(config.getStringProperty("environment")) : "Environment Overwrite Failed";
        assert config.getConfig().getBoolean("app") : "App Didn't Import Correctly";
        assert config.getConfig().getBoolean("TestFile") : "TestFile didn't import correctly";
    }
}

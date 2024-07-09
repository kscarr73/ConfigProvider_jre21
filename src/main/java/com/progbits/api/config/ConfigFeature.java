package com.progbits.api.config;

/**
 * Define a Feature to Expand ConfigProvider
 * 
 * @author scarr
 */
public interface ConfigFeature {
    /**
     * Configure the Feature with the ConfigProvider
     */
    void configure(ConfigProvider instance);
}

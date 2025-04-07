package com.progbits.api.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.progbits.api.exception.ApiClassNotFoundException;
import com.progbits.api.exception.ApiException;
import com.progbits.api.model.ApiObject;
import com.progbits.api.parser.JsonObjectParser;
import com.progbits.api.parser.YamlObjectParser;
import com.progbits.api.utils.ApiResources;
import com.progbits.api.utils.service.ApiInstance;
import com.progbits.api.utils.service.ApiService;
import java.util.ArrayList;
import java.util.List;

public class ConfigProvider implements ApiService {

    private static final Logger log = LoggerFactory.getLogger(ConfigProvider.class);

    private static final ApiInstance<ConfigProvider> instance = new ApiInstance<>();
    
    public static ConfigProvider getInstance() {
        return instance.getInstance(ConfigProvider.class);
    }
    
    public static enum ConfigTypes {
        JSON,
        YAML
    }

    private static ConfigTypes configType = ConfigTypes.YAML;

    private static String CONFIG_EXT = "." + configType.name().toLowerCase();
    
    public static void setConfigType(ConfigTypes newConfigType) {
        configType = newConfigType;
        CONFIG_EXT = "." + configType.name().toLowerCase();
    }

    @Override
    public void configure() {
        if (System.getProperty("CONFIG_FILE") != null) {
            log.info("Config File Used: " + System.getProperty("CONFIG_FILE"));
            this.setFileSystemConfig(System.getProperty("CONFIG_FILE"));
        } else {
            log.info("Config Properties Not Used");
        }

        if (System.getProperty("CONFIG_ENV_VARS") == null) {
            this.setEnvVars();
        }

        this.setConfig(this.getStringProperty("APP_CONFIG"));

        this.setFileConfig("config/default" + CONFIG_EXT);

        if (this.getConfig().isSet("APP_INIT")) {
            this.setFileConfig("config/" + getStringProperty("APP_INIT") + CONFIG_EXT);
        } else if (this.getConfig().isSet("APP_ENV")) {
            this.setFileConfig("config/" + getStringProperty("APP_ENV") + CONFIG_EXT);
        }

        if (!configFeatures.isEmpty()) {
            for (var entry : configFeatures) {
                entry.configure(this);
            }
        }
    }

    private static List<ConfigFeature> configFeatures = new ArrayList<>();

    private ApiObject _config = new ApiObject();
    private static final JsonObjectParser jsonParser = new JsonObjectParser(true);
    private static final String __OBFUSCATE = "OBF:";

    private ApiResources apiResources = ApiResources.getInstance();
    
    public static void registerFeature(ConfigFeature feature) {
        configFeatures.add(feature);
    }

    private void setConfig(String config) {
        if (config != null) {
            try {
                _config.putAll(jsonParser.parseSingle(new StringReader(config)));
            } catch (ApiClassNotFoundException | ApiException ex) {
                log.error("Configuration Parsing Failed");
            }

        }
    }

    private void setEnvVars() {
        for (var entry : System.getenv().entrySet()) {
            _config.put(entry.getKey(), entry.getValue());
        }
    }

    private void setFileSystemConfig(String fileName) {
        InputStream inputStream = null;

        try {
            if (Files.exists(Paths.get(fileName))) {
                inputStream = Files.newInputStream(Paths.get(fileName));

                if (fileName.endsWith("properties")) {
                    Properties props = new Properties();
                    props.load(inputStream);

                    props.forEach((k, v) -> {
                        _config.setString((String) k, (String) v);
                    });
                } else if (fileName.endsWith("json")) {
                    JsonObjectParser parser = new JsonObjectParser(true);

                    ApiObject objProps = parser.parseSingle(new InputStreamReader(inputStream));

                    _config.putAll(objProps);
                } else if (fileName.endsWith("yaml")) {
                    YamlObjectParser yamlParser = new YamlObjectParser(true);

                    _config.putAll(yamlParser.parseSingle(new InputStreamReader(inputStream)));
                }
            }
        } catch (Exception io) {
            log.error("Failed to parse {}", fileName, io);
        }
    }

    private void setFileConfig(String fileName) {
        BufferedReader buffRead = null;

        try (InputStream inputStream = apiResources.getResourceInputStream(fileName);) {
            if (inputStream != null) {
                buffRead = new BufferedReader(new InputStreamReader(inputStream));

                if (fileName.endsWith("yaml")) {
                    YamlObjectParser yamlParser = new YamlObjectParser(true);

                    addEntries(yamlParser.parseSingle(buffRead));
                } else {
                    addEntries(jsonParser.parseSingle(buffRead));
                }
            }
        } catch (ApiException | IOException | ApiClassNotFoundException aex) {
            log.error("Configuration Parsing Failed");
        } 
    }
    
    public void addEntries(ApiObject subject) {
        for (var entry : subject.entrySet()) {
            if ("$import".equals(entry.getKey()) 
                   && subject.getType(entry.getKey()) == ApiObject.TYPE_STRINGARRAY) {
                for (String fileName : subject.getStringArray(entry.getKey())) {
                    setFileConfig("config/" + fileName + CONFIG_EXT);
                }
            } else if (entry.getValue() instanceof String strVal) {
                if (strVal.contains("config~")) {
                    String[] plusKey = strVal.split("\\+");
                    String[] splitKey = strVal.split("config~");
                    String subjectVal = null;

                    if (splitKey.length > 1) {
                        subjectVal = _config.getString(splitKey[1]);
                    }

                    if (plusKey.length > 1) {
                        _config.put(entry.getKey(), plusKey[0] + subjectVal);
                    } else {
                        _config.put(entry.getKey(), subjectVal);
                    }
                } else {
                    _config.put(entry.getKey(), entry.getValue());
                }
            } else {
                _config.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public ApiObject getConfig() {
        return _config;
    }

    public Integer getIntProperty(String name) {
        switch (_config.getType(name)) {
            case ApiObject.TYPE_INTEGER -> {
                return _config.getInteger(name);
            }

            case ApiObject.TYPE_STRING -> {
                String strVal = _config.getString(name);
                return Integer.valueOf(strVal);
            }

            default -> {
                return null;
            }
        }
    }

    public Integer getIntProperty(String name, Integer defVal) {
        switch (_config.getType(name)) {
            case ApiObject.TYPE_INTEGER -> {
                return _config.getInteger(name, defVal);
            }

            case ApiObject.TYPE_STRING -> {
                String strVal = _config.getString(name);

                if (null == strVal) {
                    strVal = String.valueOf(defVal);
                }

                return Integer.valueOf(strVal);
            }

            default -> {
                return defVal;
            }
        }
    }

    public String getStringProperty(String name) {
        String strRet = _config.getString(name);

        if (strRet != null && strRet.startsWith(__OBFUSCATE)) {
            strRet = deobfuscate(strRet);
        }

        return strRet;
    }

    public String getStringProperty(String name, String defVal) {
        String strRet = _config.getString(name, defVal);

        if (strRet != null && strRet.startsWith(__OBFUSCATE)) {
            strRet = deobfuscate(strRet);
        }

        return strRet;
    }

    public Map<String, String> getStringProperties() {
        Map<String, String> retMap = new HashMap<>();

        for (var entry : _config.entrySet()) {
            if (entry.getValue() instanceof String value) {
                if (value.startsWith(__OBFUSCATE)) {
                    value = deobfuscate(value);
                }

                retMap.put(entry.getKey(), value);
            }
        }

        return retMap;
    }

    public Double getDoubleProperty(String name) {
        return _config.getDouble(name);
    }

    public static String obfuscate(String s) {
        StringBuilder buf = new StringBuilder();
        byte[] b = s.getBytes(StandardCharsets.UTF_8);

        buf.append(__OBFUSCATE);
        for (int i = 0; i < b.length; i++) {
            byte b1 = b[i];
            byte b2 = b[b.length - (i + 1)];
            if (b1 < 0 || b2 < 0) {
                int i0 = (0xff & b1) * 256 + (0xff & b2);
                String x = Integer.toString(i0, 36).toLowerCase(Locale.ENGLISH);
                buf.append("U0000", 0, 5 - x.length());
                buf.append(x);
            } else {
                int i1 = 127 + b1 + b2;
                int i2 = 127 + b1 - b2;
                int i0 = i1 * 256 + i2;
                String x = Integer.toString(i0, 36).toLowerCase(Locale.ENGLISH);

                int j0 = Integer.parseInt(x, 36);
                int j1 = (i0 / 256);
                int j2 = (i0 % 256);
                byte bx = (byte) ((j1 + j2 - 254) / 2);

                buf.append("000", 0, 4 - x.length());
                buf.append(x);
            }
        }
        return buf.toString();
    }

    public static String deobfuscate(String s) {
        if (s.startsWith(__OBFUSCATE)) {
            s = s.substring(4);
        }

        byte[] b = new byte[s.length() / 2];
        int l = 0;
        for (int i = 0; i < s.length(); i += 4) {
            if (s.charAt(i) == 'U') {
                i++;
                String x = s.substring(i, i + 4);
                int i0 = Integer.parseInt(x, 36);
                byte bx = (byte) (i0 >> 8);
                b[l++] = bx;
            } else {
                String x = s.substring(i, i + 4);
                int i0 = Integer.parseInt(x, 36);
                int i1 = (i0 / 256);
                int i2 = (i0 % 256);
                byte bx = (byte) ((i1 + i2 - 254) / 2);
                b[l++] = bx;
            }
        }

        return new String(b, 0, l, StandardCharsets.UTF_8);
    }
}

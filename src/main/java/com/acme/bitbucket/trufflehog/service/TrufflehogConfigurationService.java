package com.acme.bitbucket.trufflehog.service;

import com.acme.bitbucket.trufflehog.model.TrufflehogSettings;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@Named
@Component
public class TrufflehogConfigurationService {
    private static final Logger LOG = LoggerFactory.getLogger(TrufflehogConfigurationService.class);
    private static final String PLUGIN_SETTINGS_KEY = "com.acme.bitbucket.trufflehog.settings";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public TrufflehogConfigurationService(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    public TrufflehogSettings getSettings() {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        Object raw = settings.get(PLUGIN_SETTINGS_KEY);
        if (raw == null) {
            return new TrufflehogSettings();
        }
        try {
            return objectMapper.readValue(raw.toString(), TrufflehogSettings.class);
        } catch (Exception ex) {
            LOG.warn("Failed to deserialise TruffleHog settings - using defaults", ex);
            return new TrufflehogSettings();
        }
    }

    public void saveSettings(TrufflehogSettings payload) {
        if (payload == null) {
            throw new IllegalArgumentException("settings payload must not be null");
        }
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        try {
            settings.put(PLUGIN_SETTINGS_KEY, objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to save TruffleHog settings", ex);
        }
    }
}

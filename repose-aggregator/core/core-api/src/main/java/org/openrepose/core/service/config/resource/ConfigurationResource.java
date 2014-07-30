package org.openrepose.core.service.config.resource;

import java.io.IOException;
import java.io.InputStream;

public interface ConfigurationResource<T extends ConfigurationResource> {

    boolean updated() throws IOException;

    boolean exists() throws IOException;

    String name();

    InputStream newInputStream() throws IOException;
}
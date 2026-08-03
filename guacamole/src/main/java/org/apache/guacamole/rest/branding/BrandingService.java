/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.rest.branding;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.environment.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service which stores and retrieves the deployment's branding: the site name
 * shown throughout the interface and the logo image. Branding is stored as
 * files beneath GUACAMOLE_HOME rather than in a database, because the login
 * screen must render it before any user is authenticated and therefore before
 * any data source is available.
 */
@Singleton
public class BrandingService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(BrandingService.class);

    /**
     * The name of the directory beneath GUACAMOLE_HOME which holds branding.
     */
    private static final String BRANDING_DIRECTORY = "branding";

    /**
     * The name of the file which holds the branding settings.
     */
    private static final String SETTINGS_FILE = "branding.json";

    /**
     * The base name of the stored logo, without extension.
     */
    private static final String LOGO_BASE_NAME = "logo";

    /**
     * The largest logo accepted, in bytes.
     */
    public static final int MAX_LOGO_BYTES = 256 * 1024;

    /**
     * The image types which may be uploaded as a logo, mapped to the file
     * extension used when storing them. Vector formats are deliberately
     * excluded: the logo is served to unauthenticated visitors on the login
     * screen, and SVG can carry script.
     */
    private static final Map<String, String> ALLOWED_TYPES = new HashMap<String, String>();
    static {
        ALLOWED_TYPES.put("image/png",  "png");
        ALLOWED_TYPES.put("image/jpeg", "jpg");
        ALLOWED_TYPES.put("image/webp", "webp");
        ALLOWED_TYPES.put("image/gif",  "gif");
    }

    /**
     * Mapper for reading and writing the settings file.
     */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * The Guacamole server environment, used to locate GUACAMOLE_HOME.
     */
    @Inject
    private Environment environment;

    /**
     * Returns the directory holding branding, creating it if necessary.
     *
     * @return
     *     The branding directory.
     *
     * @throws GuacamoleException
     *     If the directory does not exist and cannot be created.
     */
    private File getBrandingDirectory() throws GuacamoleException {

        File directory = new File(environment.getGuacamoleHome(), BRANDING_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs())
            throw new GuacamoleServerException("The branding directory \""
                    + directory + "\" does not exist and could not be created. "
                    + "Ensure GUACAMOLE_HOME is writable.");

        return directory;

    }

    /**
     * Returns the stored branding settings, or defaults if none are stored.
     *
     * @return
     *     The current branding settings.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSettings() {

        Map<String, Object> settings = new HashMap<String, Object>();

        try {

            File file = new File(getBrandingDirectory(), SETTINGS_FILE);
            if (file.exists())
                settings = mapper.readValue(file, Map.class);

        }

        // Branding is cosmetic: if it cannot be read, the application must
        // still start and serve its defaults
        catch (GuacamoleException | IOException e) {
            logger.warn("Branding settings could not be read: {}", e.getMessage());
            logger.debug("Unable to read branding settings.", e);
        }

        return settings;

    }

    /**
     * Stores the given site name, or clears it if null or blank.
     *
     * @param siteName
     *     The site name to store.
     *
     * @throws GuacamoleException
     *     If the settings cannot be written.
     */
    public void setSiteName(String siteName) throws GuacamoleException {

        Map<String, Object> settings = getSettings();

        if (siteName == null || siteName.trim().isEmpty())
            settings.remove("siteName");
        else {

            // Bound the length; this string is rendered in the interface
            String trimmed = siteName.trim();
            if (trimmed.length() > 128)
                throw new GuacamoleClientException("The site name may be at most 128 characters.");

            settings.put("siteName", trimmed);

        }

        writeSettings(settings);

    }

    /**
     * Writes the given settings to storage.
     *
     * @param settings
     *     The settings to write.
     *
     * @throws GuacamoleException
     *     If the settings cannot be written.
     */
    private void writeSettings(Map<String, Object> settings) throws GuacamoleException {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(
                    new File(getBrandingDirectory(), SETTINGS_FILE), settings);
        }
        catch (IOException e) {
            throw new GuacamoleServerException("Branding settings could not be written.", e);
        }
    }

    /**
     * Returns the stored logo file, or null if no logo has been uploaded.
     *
     * @return
     *     The stored logo file, or null if there is none.
     */
    public File getLogoFile() {

        Object stored = getSettings().get("logoFile");
        if (stored == null)
            return null;

        try {

            File file = new File(getBrandingDirectory(), stored.toString());

            // Guard against a settings file naming something outside the
            // branding directory
            if (!file.getCanonicalPath().startsWith(
                    getBrandingDirectory().getCanonicalPath() + File.separator))
                return null;

            return file.exists() ? file : null;

        }
        catch (GuacamoleException | IOException e) {
            logger.warn("Stored logo could not be read: {}", e.getMessage());
            return null;
        }

    }

    /**
     * Returns the media type of the stored logo, or null if there is no logo.
     *
     * @return
     *     The media type of the stored logo, or null if there is none.
     */
    public String getLogoType() {
        Object type = getSettings().get("logoType");
        return type == null ? null : type.toString();
    }

    /**
     * Stores the given image as the logo, replacing any existing logo.
     *
     * @param data
     *     The raw image data.
     *
     * @param declaredType
     *     The media type declared by the client, which is verified against
     *     the actual content before being trusted.
     *
     * @throws GuacamoleException
     *     If the image is not an accepted format, is too large, or cannot be
     *     written.
     */
    public void setLogo(byte[] data, String declaredType) throws GuacamoleException {

        if (data == null || data.length == 0)
            throw new GuacamoleClientException("No image was provided.");

        if (data.length > MAX_LOGO_BYTES)
            throw new GuacamoleClientException("The logo may be at most "
                    + (MAX_LOGO_BYTES / 1024) + " KB.");

        // Determine the type from the content itself rather than trusting
        // what the client declared
        String detected = detectType(data);
        if (detected == null)
            throw new GuacamoleClientException("The logo must be a PNG, JPEG, "
                    + "WebP, or GIF image. Vector images are not accepted.");

        String extension = ALLOWED_TYPES.get(detected);

        try {

            // Remove any previously-stored logo, whatever its format
            removeLogo();

            File file = new File(getBrandingDirectory(), LOGO_BASE_NAME + '.' + extension);
            Files.write(file.toPath(), data);

            Map<String, Object> settings = getSettings();
            settings.put("logoFile", file.getName());
            settings.put("logoType", detected);
            writeSettings(settings);

        }
        catch (IOException e) {
            throw new GuacamoleServerException("The logo could not be written.", e);
        }

    }

    /**
     * Removes the stored logo, if any.
     *
     * @throws GuacamoleException
     *     If the stored logo cannot be removed.
     */
    public void removeLogo() throws GuacamoleException {

        File existing = getLogoFile();
        if (existing != null && !existing.delete())
            logger.warn("Previous logo \"{}\" could not be removed.", existing);

        Map<String, Object> settings = getSettings();
        settings.remove("logoFile");
        settings.remove("logoType");
        writeSettings(settings);

    }

    /**
     * Returns the media type of the given image data as determined by its
     * leading bytes, or null if the data is not an accepted raster image.
     * The declared content type is deliberately ignored: this file is served
     * to unauthenticated visitors, so its type must be established from the
     * content.
     *
     * @param data
     *     The image data to inspect.
     *
     * @returns
     *     The detected media type, or null if the data is not an accepted
     *     image format.
     */
    private String detectType(byte[] data) {

        if (data.length < 12)
            return null;

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && (data[4] & 0xFF) == 0x0D && (data[5] & 0xFF) == 0x0A
                && (data[6] & 0xFF) == 0x1A && (data[7] & 0xFF) == 0x0A)
            return "image/png";

        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF)
            return "image/jpeg";

        // GIF: "GIF87a" or "GIF89a"
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8')
            return "image/gif";

        // WebP: "RIFF" .... "WEBP"
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P')
            return "image/webp";

        return null;

    }

}

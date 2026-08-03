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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.net.auth.permission.SystemPermission;
import org.apache.guacamole.net.auth.permission.SystemPermissionSet;
import org.apache.guacamole.rest.TokenParam;
import org.apache.guacamole.rest.auth.AuthenticationService;

/**
 * REST service exposing the deployment's branding. Reads are deliberately
 * unauthenticated: the login screen renders the site name and logo before
 * anyone has signed in, exactly as it already does for translations. Writes
 * require system administration privileges.
 */
@Path("/branding")
@Singleton
public class BrandingRESTService {

    /**
     * Service for storing and retrieving branding.
     */
    @Inject
    private BrandingService brandingService;

    /**
     * Service for resolving authentication tokens to sessions.
     */
    @Inject
    private AuthenticationService authenticationService;

    /**
     * Verifies that the user owning the given token holds system
     * administration privileges, throwing an exception otherwise.
     *
     * @param authToken
     *     The authentication token of the requesting user.
     *
     * @throws GuacamoleException
     *     If the token is invalid or the user is not an administrator.
     */
    private void requireAdministrator(String authToken) throws GuacamoleException {

        for (UserContext userContext : authenticationService.getUserContexts(authToken)) {

            SystemPermissionSet permissions =
                    userContext.self().getEffectivePermissions().getSystemPermissions();

            if (permissions.hasPermission(SystemPermission.Type.ADMINISTER))
                return;

        }

        throw new GuacamoleSecurityException("Permission denied.");

    }

    /**
     * Returns the current branding. Available without authentication, as the
     * login screen renders it.
     *
     * @return
     *     A map describing the site name and whether a logo is available.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getBranding() {

        Map<String, Object> branding = new HashMap<String, Object>();
        Map<String, Object> settings = brandingService.getSettings();

        branding.put("siteName", settings.get("siteName"));
        branding.put("hasLogo", brandingService.getLogoFile() != null);

        return branding;

    }

    /**
     * Updates the site name.
     *
     * @param authToken
     *     The authentication token of the requesting administrator.
     *
     * @param branding
     *     A map which may contain a "siteName" entry.
     *
     * @throws GuacamoleException
     *     If permission is denied or the value cannot be stored.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public void updateBranding(@TokenParam String authToken,
            Map<String, Object> branding) throws GuacamoleException {

        requireAdministrator(authToken);

        Object siteName = branding.get("siteName");
        brandingService.setSiteName(siteName == null ? null : siteName.toString());

    }

    /**
     * Returns the stored logo image. Available without authentication, as the
     * login screen renders it.
     *
     * @return
     *     The stored logo, or 404 if no logo has been uploaded.
     *
     * @throws GuacamoleException
     *     If the stored logo cannot be read.
     */
    @GET
    @Path("logo")
    public Response getLogo() throws GuacamoleException {

        File logo = brandingService.getLogoFile();
        if (logo == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        try {

            byte[] data = Files.readAllBytes(logo.toPath());

            // The type is the one detected from the content when the image
            // was stored, never a client-supplied value, and sniffing is
            // disabled so the browser cannot reinterpret it
            return Response.ok(data, brandingService.getLogoType())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Cache-Control", "public, max-age=60")
                    .header("ETag", '"' + Long.toHexString(logo.lastModified()) + '"')
                    .build();

        }
        catch (IOException e) {
            throw new GuacamoleServerException("The stored logo could not be read.", e);
        }

    }

    /**
     * Replaces the logo with the given image. The image must be a PNG, JPEG,
     * WebP, or GIF; the format is determined from the content rather than
     * from what the client declares.
     *
     * @param authToken
     *     The authentication token of the requesting administrator.
     *
     * @param data
     *     The raw image data.
     *
     * @throws GuacamoleException
     *     If permission is denied, or the image is unacceptable.
     */
    @POST
    @Path("logo")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void updateLogo(@TokenParam String authToken, byte[] data)
            throws GuacamoleException {

        requireAdministrator(authToken);
        brandingService.setLogo(data, null);

    }

    /**
     * Removes the logo, restoring the default.
     *
     * @param authToken
     *     The authentication token of the requesting administrator.
     *
     * @throws GuacamoleException
     *     If permission is denied or the logo cannot be removed.
     */
    @DELETE
    @Path("logo")
    public void deleteLogo(@TokenParam String authToken) throws GuacamoleException {
        requireAdministrator(authToken);
        brandingService.removeLogo();
    }

}

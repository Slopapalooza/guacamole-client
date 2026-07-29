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

package org.apache.guacamole.auth.jdbc.rest;

import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.auth.jdbc.connectiongroup.ConnectionGroupParameterModel;
import org.apache.guacamole.auth.jdbc.connectiongroup.GroupDefaultsService;
import org.apache.guacamole.auth.jdbc.user.ModeledAuthenticatedUser;
import org.apache.guacamole.net.auth.permission.ObjectPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST resource exposing inheritable connection group default parameters.
 * Returned by ModeledUserContext.getResource() and therefore mounted by the
 * web application at /api/session/ext/{dataSource}.
 */
@Produces(MediaType.APPLICATION_JSON)
public class GroupDefaultsResource {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(GroupDefaultsResource.class);

    /**
     * Service for managing connection group defaults.
     */
    @Inject
    private GroupDefaultsService groupDefaultsService;

    /**
     * The user accessing this resource.
     */
    private ModeledAuthenticatedUser currentUser;

    /**
     * A single inherited parameter value together with the group it was
     * inherited from.
     */
    public static class APIInheritedParameter {

        /**
         * The effective value of the parameter.
         */
        private final String value;

        /**
         * The identifier of the connection group supplying the value.
         */
        private final String source;

        /**
         * The human-readable name of the connection group supplying the
         * value.
         */
        private final String sourceName;

        /**
         * Creates a new APIInheritedParameter describing the given value and
         * its source group.
         *
         * @param value
         *     The effective value of the parameter.
         *
         * @param source
         *     The identifier of the group supplying the value.
         *
         * @param sourceName
         *     The name of the group supplying the value.
         */
        public APIInheritedParameter(String value, String source, String sourceName) {
            this.value = value;
            this.source = source;
            this.sourceName = sourceName;
        }

        /**
         * Returns the effective value of the parameter.
         *
         * @return
         *     The effective value of the parameter.
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the identifier of the group supplying the value.
         *
         * @return
         *     The identifier of the group supplying the value.
         */
        public String getSource() {
            return source;
        }

        /**
         * Returns the name of the group supplying the value.
         *
         * @return
         *     The name of the group supplying the value.
         */
        public String getSourceName() {
            return sourceName;
        }

    }

    /**
     * Initializes this resource for use by the given user.
     *
     * @param currentUser
     *     The user accessing this resource.
     */
    public void init(ModeledAuthenticatedUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Verifies that the current user holds system-level administrative
     * privileges, throwing an exception otherwise.
     *
     * @throws GuacamoleException
     *     If the current user is not privileged.
     */
    private void requirePrivileged() throws GuacamoleException {
        if (currentUser == null || !currentUser.isPrivileged())
            throw new GuacamoleSecurityException("Permission denied.");
    }

    /**
     * Reduces the given root-first parameter list to a map of parameter name
     * to inherited value with provenance, where nearer groups override
     * farther groups.
     *
     * @param models
     *     The root-first list of applicable defaults.
     *
     * @return
     *     A map of parameter name to inherited value and source.
     */
    private Map<String, APIInheritedParameter> toProvenanceMap(
            Iterable<ConnectionGroupParameterModel> models) {

        Map<String, APIInheritedParameter> inherited =
                new LinkedHashMap<String, APIInheritedParameter>();

        for (ConnectionGroupParameterModel model : models) {
            inherited.put(model.getName(), new APIInheritedParameter(
                    model.getValue(), model.getGroupIdentifier(),
                    model.getGroupName()));
        }

        return inherited;

    }

    /**
     * Returns the default parameters stored directly on the given connection
     * group.
     *
     * @param groupIdentifier
     *     The identifier of the connection group.
     *
     * @return
     *     A map of parameter name to stored default value.
     *
     * @throws GuacamoleException
     *     If permission is denied.
     */
    @GET
    @Path("groupDefaults/{groupIdentifier}")
    public Map<String, String> getGroupDefaults(
            @PathParam("groupIdentifier") String groupIdentifier)
            throws GuacamoleException {

        requirePrivileged();
        return groupDefaultsService.getDefaults(groupIdentifier);

    }

    /**
     * Replaces the default parameters stored on the given connection group.
     *
     * @param groupIdentifier
     *     The identifier of the connection group.
     *
     * @param defaults
     *     The new defaults, as a map of parameter name to value. Empty
     *     values remove the default.
     *
     * @throws GuacamoleException
     *     If permission is denied or a parameter is not inheritable.
     */
    @PUT
    @Path("groupDefaults/{groupIdentifier}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setGroupDefaults(
            @PathParam("groupIdentifier") String groupIdentifier,
            Map<String, String> defaults) throws GuacamoleException {

        requirePrivileged();
        groupDefaultsService.setDefaults(groupIdentifier, defaults);

        // Values are loggable by construction: the allowlist permanently
        // excludes credential-class parameters
        logger.info("User \"{}\" set default parameters of connection group "
                + "{}: {}", currentUser.getIdentifier(), groupIdentifier,
                defaults);

    }

    /**
     * Returns the defaults which would arrive at the given connection group
     * from its ancestors, with provenance.
     *
     * @param groupIdentifier
     *     The identifier of the connection group.
     *
     * @return
     *     A map of parameter name to inherited value and source group.
     *
     * @throws GuacamoleException
     *     If permission is denied.
     */
    @GET
    @Path("groupDefaults/{groupIdentifier}/inherited")
    public Map<String, APIInheritedParameter> getGroupInheritedDefaults(
            @PathParam("groupIdentifier") String groupIdentifier)
            throws GuacamoleException {

        requirePrivileged();

        String parent = groupDefaultsService.getParentGroupIdentifier(groupIdentifier);
        return toProvenanceMap(groupDefaultsService.getEffectiveDefaultModels(parent));

    }

    /**
     * Returns the inherited (never local) default values applicable to the
     * given connection, with provenance, as needed to annotate the
     * connection edit form.
     *
     * @param connectionIdentifier
     *     The identifier of the connection.
     *
     * @return
     *     A map of parameter name to inherited value and source group.
     *
     * @throws GuacamoleException
     *     If permission is denied.
     */
    @GET
    @Path("connectionDefaults/{connectionIdentifier}")
    public Map<String, APIInheritedParameter> getConnectionDefaults(
            @PathParam("connectionIdentifier") String connectionIdentifier)
            throws GuacamoleException {

        // Mirror the gate protecting connection parameter reads: UPDATE on
        // the connection (or system privileges)
        if (currentUser == null)
            throw new GuacamoleSecurityException("Permission denied.");

        if (!currentUser.isPrivileged()
                && !currentUser.getUser().getEffectivePermissions()
                        .getConnectionPermissions().hasPermission(
                                ObjectPermission.Type.UPDATE, connectionIdentifier))
            throw new GuacamoleSecurityException("Permission denied.");

        String parent = groupDefaultsService.getConnectionParentGroupIdentifier(connectionIdentifier);
        return toProvenanceMap(groupDefaultsService.getEffectiveDefaultModels(parent));

    }

}

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

package org.apache.guacamole.auth.jdbc.connectiongroup;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service which manages inheritable default parameters stored on connection
 * groups. Defaults flow down to descendant connections at connect time:
 * a connection's own parameter always wins, otherwise the value of the
 * nearest ancestor group defining a default for that parameter applies.
 * Nothing is ever copied into connection parameter rows.
 */
@Singleton
public class GroupDefaultsService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(GroupDefaultsService.class);

    /**
     * The names of all parameters which may be stored as inheritable
     * connection group defaults. Enforced both when defaults are saved and
     * again when they are applied at connect time (defense in depth against
     * rows inserted outside the API).
     *
     * Trust-affecting parameters (ignore-cert, security, cert-tofu) are
     * deliberately absent until the inheritance-visibility UI ships.
     * Credentials, file paths, program execution, and addressing parameters
     * are permanently excluded.
     */
    public static final Set<String> INHERITABLE_PARAMETERS =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(

        // Session/identity
        "domain",

        // Display
        "resize-method",
        "secondary-monitors",
        "color-depth",
        "force-lossless",
        "dpi",
        "server-layout",
        "timezone",

        // Performance/appearance flags
        "enable-font-smoothing",
        "enable-wallpaper",
        "enable-theming",
        "enable-full-window-drag",
        "enable-desktop-composition",
        "enable-menu-animations",
        "disable-bitmap-caching",
        "disable-offscreen-caching",
        "disable-glyph-caching"

    )));

    /**
     * Mapper for connection group default parameters.
     */
    @Inject
    private ConnectionGroupParameterMapper parameterMapper;

    /**
     * Returns whether the given identifier is a usable database identifier
     * (non-null and numeric). The root group's pseudo-identifier and null
     * parents resolve to false.
     *
     * @param identifier
     *     The identifier to test.
     *
     * @return
     *     true if the identifier can be used in queries, false otherwise.
     */
    private boolean isDatabaseIdentifier(String identifier) {

        if (identifier == null || identifier.isEmpty())
            return false;

        for (int i = 0; i < identifier.length(); i++) {
            if (!Character.isDigit(identifier.charAt(i)))
                return false;
        }

        return true;

    }

    /**
     * Returns the default parameters stored directly on the given connection
     * group, as a map of parameter name to value.
     *
     * @param groupIdentifier
     *     The identifier of the connection group.
     *
     * @return
     *     A map of the group's own default parameters. Empty if the group
     *     stores none.
     */
    public Map<String, String> getDefaults(String groupIdentifier) {

        Map<String, String> defaults = new LinkedHashMap<String, String>();

        if (!isDatabaseIdentifier(groupIdentifier))
            return defaults;

        for (ConnectionGroupParameterModel parameter : parameterMapper.select(groupIdentifier))
            defaults.put(parameter.getName(), parameter.getValue());

        return defaults;

    }

    /**
     * Replaces the default parameters of the given connection group with the
     * given name/value pairs. Empty values are skipped (storing no row), and
     * every name must be within INHERITABLE_PARAMETERS.
     *
     * @param groupIdentifier
     *     The identifier of the connection group whose defaults should be
     *     replaced.
     *
     * @param defaults
     *     The new default parameters, as a map of parameter name to value.
     *
     * @throws GuacamoleException
     *     If any parameter name is not inheritable, or the group identifier
     *     is not valid.
     */
    public void setDefaults(String groupIdentifier, Map<String, String> defaults)
            throws GuacamoleException {

        if (!isDatabaseIdentifier(groupIdentifier))
            throw new GuacamoleClientException("Invalid connection group identifier.");

        // Build models, validating against the allowlist
        Collection<ConnectionGroupParameterModel> models =
                new ArrayList<ConnectionGroupParameterModel>();

        for (Map.Entry<String, String> parameter : defaults.entrySet()) {

            // Reject non-inheritable parameter names outright
            if (!INHERITABLE_PARAMETERS.contains(parameter.getKey()))
                throw new GuacamoleClientException("Parameter \""
                        + parameter.getKey() + "\" is not inheritable.");

            // Skip empty values - absence of a row is the "no default" state
            String value = parameter.getValue();
            if (value == null || value.isEmpty())
                continue;

            ConnectionGroupParameterModel model = new ConnectionGroupParameterModel();
            model.setGroupIdentifier(groupIdentifier);
            model.setName(parameter.getKey());
            model.setValue(value);
            models.add(model);

        }

        // Replace all stored defaults
        parameterMapper.delete(groupIdentifier);
        if (!models.isEmpty())
            parameterMapper.insert(models);

    }

    /**
     * Returns the effective inheritable defaults arriving at the given
     * connection group, taking the group itself and all ancestors into
     * account. Nearer groups override farther groups. Only allowlisted
     * parameters are included.
     *
     * @param groupIdentifier
     *     The identifier of the connection group at which resolution starts,
     *     typically the parent group of a connection.
     *
     * @return
     *     A map of parameter name to effective value. Empty if no defaults
     *     apply.
     */
    public Map<String, String> getEffectiveDefaults(String groupIdentifier) {

        Map<String, String> defaults = new LinkedHashMap<String, String>();

        for (ConnectionGroupParameterModel parameter : getEffectiveDefaultModels(groupIdentifier))
            defaults.put(parameter.getName(), parameter.getValue());

        return defaults;

    }

    /**
     * Returns the raw, root-first list of allowlisted default parameters of
     * the given group and its ancestors, with group names and depths
     * populated for provenance. Iterating the list and overwriting by
     * parameter name yields nearest-ancestor-wins resolution.
     *
     * @param groupIdentifier
     *     The identifier of the connection group at which resolution starts.
     *
     * @return
     *     The root-first list of applicable default parameters. Empty if no
     *     defaults apply.
     */
    public List<ConnectionGroupParameterModel> getEffectiveDefaultModels(String groupIdentifier) {

        List<ConnectionGroupParameterModel> models =
                new ArrayList<ConnectionGroupParameterModel>();

        if (!isDatabaseIdentifier(groupIdentifier))
            return models;

        for (ConnectionGroupParameterModel parameter : parameterMapper.selectEffective(groupIdentifier)) {

            // Skip - and loudly note - any stored default outside the
            // allowlist, regardless of how the row came to exist
            if (!INHERITABLE_PARAMETERS.contains(parameter.getName())) {
                logger.warn("Ignoring non-inheritable parameter \"{}\" stored "
                        + "as a default on connection group {}.",
                        parameter.getName(), parameter.getGroupIdentifier());
                continue;
            }

            models.add(parameter);

        }

        return models;

    }

    /**
     * Returns the identifier of the parent group of the given connection
     * group, or null if it has none.
     *
     * @param groupIdentifier
     *     The identifier of the connection group.
     *
     * @return
     *     The identifier of the parent group, or null.
     */
    public String getParentGroupIdentifier(String groupIdentifier) {

        if (!isDatabaseIdentifier(groupIdentifier))
            return null;

        return parameterMapper.selectParentIdentifier(groupIdentifier);

    }

    /**
     * Returns the identifier of the parent group of the given connection, or
     * null if the connection sits at the root.
     *
     * @param connectionIdentifier
     *     The identifier of the connection.
     *
     * @return
     *     The identifier of the connection's parent group, or null.
     */
    public String getConnectionParentGroupIdentifier(String connectionIdentifier) {

        if (!isDatabaseIdentifier(connectionIdentifier))
            return null;

        return parameterMapper.selectConnectionParentIdentifier(connectionIdentifier);

    }

}

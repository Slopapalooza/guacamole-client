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

/**
 * A single inheritable default parameter name/value pair belonging to a
 * connection group. When queried as part of an ancestry walk, the model
 * additionally carries the name of the owning group and its depth within
 * the walk, for provenance display.
 */
public class ConnectionGroupParameterModel {

    /**
     * The identifier of the connection group associated with this parameter.
     */
    private String groupIdentifier;

    /**
     * The human-readable name of the connection group associated with this
     * parameter. Populated only by ancestry queries.
     */
    private String groupName;

    /**
     * The name of the parameter.
     */
    private String name;

    /**
     * The value the parameter is set to.
     */
    private String value;

    /**
     * The number of levels between the connection group that began the
     * ancestry walk and the group owning this parameter, where zero is the
     * starting group itself. Populated only by ancestry queries.
     */
    private int depth;

    /**
     * Returns the identifier of the connection group associated with this
     * parameter.
     *
     * @return
     *     The identifier of the connection group associated with this
     *     parameter.
     */
    public String getGroupIdentifier() {
        return groupIdentifier;
    }

    /**
     * Sets the identifier of the connection group associated with this
     * parameter.
     *
     * @param groupIdentifier
     *     The identifier of the connection group to associate with this
     *     parameter.
     */
    public void setGroupIdentifier(String groupIdentifier) {
        this.groupIdentifier = groupIdentifier;
    }

    /**
     * Returns the human-readable name of the connection group associated
     * with this parameter, if populated by the query which produced this
     * model.
     *
     * @return
     *     The name of the connection group associated with this parameter,
     *     or null if the query which produced this model does not populate
     *     group names.
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Sets the human-readable name of the connection group associated with
     * this parameter.
     *
     * @param groupName
     *     The name of the connection group associated with this parameter.
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     * Returns the name of this parameter.
     *
     * @return
     *     The name of this parameter.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this parameter.
     *
     * @param name
     *     The name of this parameter.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the value of this parameter.
     *
     * @return
     *     The value of this parameter.
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of this parameter.
     *
     * @param value
     *     The value of this parameter.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the depth of the owning group within the ancestry walk which
     * produced this model, where zero is the starting group itself.
     *
     * @return
     *     The depth of the owning group within the ancestry walk.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the depth of the owning group within the ancestry walk which
     * produced this model.
     *
     * @param depth
     *     The depth of the owning group within the ancestry walk.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

}

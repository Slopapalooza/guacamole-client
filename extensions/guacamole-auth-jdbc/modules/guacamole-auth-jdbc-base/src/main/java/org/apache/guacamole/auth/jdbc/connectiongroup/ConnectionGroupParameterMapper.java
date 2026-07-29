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

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for inheritable connection group default parameters.
 */
public interface ConnectionGroupParameterMapper {

    /**
     * Returns a collection of the default parameters stored directly on the
     * connection group having the given identifier, without considering
     * ancestors.
     *
     * @param identifier
     *     The identifier of the connection group whose default parameters
     *     are to be retrieved.
     *
     * @return
     *     A collection of all default parameters stored on the connection
     *     group having the given identifier. This collection will be empty
     *     if no such group exists or the group stores no defaults.
     */
    Collection<ConnectionGroupParameterModel> select(@Param("identifier") String identifier);

    /**
     * Returns every default parameter stored on the connection group having
     * the given identifier or any of its ancestors, ordered root-first
     * (farthest ancestor to the given group itself), such that iterating the
     * result and overwriting by name yields nearest-ancestor-wins semantics.
     * Group names and walk depths are populated for provenance.
     *
     * @param identifier
     *     The identifier of the connection group at which the ancestry walk
     *     begins.
     *
     * @return
     *     All default parameters of the given group and its ancestors,
     *     ordered root-first. Empty if the group does not exist or no group
     *     in the chain stores defaults.
     */
    List<ConnectionGroupParameterModel> selectEffective(@Param("identifier") String identifier);

    /**
     * Returns the identifier of the parent connection group of the
     * connection group having the given identifier, or null if the group is
     * a direct child of the root group or does not exist.
     *
     * @param identifier
     *     The identifier of the connection group whose parent is to be
     *     retrieved.
     *
     * @return
     *     The identifier of the parent connection group, or null.
     */
    String selectParentIdentifier(@Param("identifier") String identifier);

    /**
     * Returns the identifier of the parent connection group of the
     * connection having the given identifier, or null if the connection is a
     * direct child of the root group or does not exist.
     *
     * @param identifier
     *     The identifier of the connection whose parent group is to be
     *     retrieved.
     *
     * @return
     *     The identifier of the connection's parent connection group, or
     *     null.
     */
    String selectConnectionParentIdentifier(@Param("identifier") String identifier);

    /**
     * Inserts each of the parameter model objects in the given collection as
     * new connection group default parameters.
     *
     * @param parameters
     *     The default parameters to insert.
     *
     * @return
     *     The number of rows inserted.
     */
    int insert(@Param("parameters") Collection<ConnectionGroupParameterModel> parameters);

    /**
     * Deletes all default parameters associated with the connection group
     * having the given identifier.
     *
     * @param identifier
     *     The identifier of the connection group whose default parameters
     *     should be deleted.
     *
     * @return
     *     The number of rows deleted.
     */
    int delete(@Param("identifier") String identifier);

}

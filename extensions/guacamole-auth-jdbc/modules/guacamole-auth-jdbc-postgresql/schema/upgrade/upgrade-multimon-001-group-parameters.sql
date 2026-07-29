--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

--
-- Fork upgrade script (first of the fork's own DDL; the "multimon-" prefix
-- keeps fork scripts separate from upstream "upgrade-pre-*" scripts).
--
-- Adds storage for inheritable connection group default parameters. Purely
-- additive; rollback is:
--
--     DROP TABLE guacamole_connection_group_parameter;
--
-- NOTE: there is no automatic migration mechanism. This script must be
-- applied manually to existing databases AND on any fresh install or
-- disaster-recovery rebuild.
--

CREATE TABLE guacamole_connection_group_parameter (

  connection_group_id integer       NOT NULL,
  parameter_name      varchar(128)  NOT NULL,
  parameter_value     varchar(4096) NOT NULL,

  PRIMARY KEY (connection_group_id, parameter_name),

  CONSTRAINT guacamole_connection_group_parameter_ibfk_1
    FOREIGN KEY (connection_group_id)
    REFERENCES guacamole_connection_group (connection_group_id)
    ON DELETE CASCADE

);

CREATE INDEX guacamole_connection_group_parameter_connection_group_id
    ON guacamole_connection_group_parameter (connection_group_id);

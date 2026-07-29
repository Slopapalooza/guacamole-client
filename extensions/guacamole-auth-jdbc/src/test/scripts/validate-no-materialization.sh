#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
#
# Materialization invariant: saving a connection which inherits parameter
# values must not copy those values into the connection's own parameters.
# If it did, inheritance would be silently severed on the first save with
# no visible difference in the interface.
#
set -u
cd ~/guacamole

PSQL="sudo docker compose --env-file guacamole.env exec -T postgres psql -U guacamole_user -d guacamole_db -tA -c"

fail() { echo "FAIL: $1"; exit 1; }

PW=$(grep '^GUAC_ADMIN_PASSWORD=' guacamole.env | cut -d= -f2- | tr -d '"' | tr -d "'")
PW="${PW%$'\r'}"
SECRET=$($PSQL "SELECT ua.attribute_value FROM guacamole_user_attribute ua JOIN guacamole_user u ON u.user_id=ua.user_id JOIN guacamole_entity e ON e.entity_id=u.entity_id WHERE e.name='guacadmin' AND ua.attribute_name='guac-totp-key-secret'")

sleep $(( 31 - ($(date +%s) % 30) ))
CODE=$(python3 - "$SECRET" <<'PYEOF'
import base64, hmac, hashlib, struct, time, sys
key = base64.b32decode(sys.argv[1])
digest = hmac.new(key, struct.pack('>Q', int(time.time() // 30)), hashlib.sha1).digest()
offset = digest[19] & 15
print('%06d' % ((struct.unpack('>I', digest[offset:offset+4])[0] & 0x7fffffff) % 1000000))
PYEOF
)

TOKEN=$(curl -sk https://127.0.0.1/api/tokens \
    --data-urlencode "username=guacadmin" --data-urlencode "password=$PW" \
    --data-urlencode "guac-totp=$CODE" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('authToken',''))")
[ -n "$TOKEN" ] || fail "authentication failed"
AUTH="Guacamole-Token: $TOKEN"
echo "PASS: authenticated"

# Ensure group defaults exist for the connection's ancestry
CSR=$($PSQL "SELECT connection_group_id FROM guacamole_connection_group WHERE connection_group_name='CSR'" | head -1 | tr -dc '0-9')
curl -sk -X PUT -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"domain":"csr.local"}' "https://127.0.0.1/api/session/ext/postgresql/groupDefaults/$CSR" > /dev/null

INHERITED=$(curl -sk -H "$AUTH" "https://127.0.0.1/api/session/ext/postgresql/connectionDefaults/3")
echo "$INHERITED" | grep -q '"domain"' || fail "connection 3 inherits nothing; test is meaningless"
echo "PASS: connection inherits: $INHERITED"

# Snapshot the connection's own parameter rows
BEFORE=$($PSQL "SELECT parameter_name || '=' || parameter_value FROM guacamole_connection_parameter WHERE connection_id=3 ORDER BY parameter_name")
echo "PASS: captured $(echo "$BEFORE" | grep -c . ) local parameter row(s)"

# Save the connection exactly as the edit form does: GET it, GET its
# parameters, then PUT both back unchanged
CONN=$(curl -sk -H "$AUTH" "https://127.0.0.1/api/session/data/postgresql/connections/3")
PARAMS=$(curl -sk -H "$AUTH" "https://127.0.0.1/api/session/data/postgresql/connections/3/parameters")

BODY=$(python3 - "$CONN" "$PARAMS" <<'PYEOF'
import json, sys
conn = json.loads(sys.argv[1])
conn['parameters'] = json.loads(sys.argv[2])
print(json.dumps(conn))
PYEOF
)

HTTP=$(curl -sk -o /tmp/gd-save -w "%{http_code}" -X PUT -H "$AUTH" \
    -H "Content-Type: application/json" -d "$BODY" \
    "https://127.0.0.1/api/session/data/postgresql/connections/3")
[ "$HTTP" = "200" ] || [ "$HTTP" = "204" ] || fail "save returned $HTTP: $(cat /tmp/gd-save)"
echo "PASS: connection saved unchanged ($HTTP)"

AFTER=$($PSQL "SELECT parameter_name || '=' || parameter_value FROM guacamole_connection_parameter WHERE connection_id=3 ORDER BY parameter_name")

if [ "$BEFORE" != "$AFTER" ]; then
    echo "--- before ---"; echo "$BEFORE"
    echo "--- after ----"; echo "$AFTER"
    fail "MATERIALIZATION: saving changed the connection's own parameters"
fi
echo "PASS: parameter rows unchanged after save"

# The connection must still inherit, not own, the value
$PSQL "SELECT 1 FROM guacamole_connection_parameter WHERE connection_id=3 AND parameter_name='domain'" | grep -q 1 \
    && fail "MATERIALIZATION: inherited 'domain' was written as a local parameter"
echo "PASS: inherited parameter still not stored locally"

STILL=$(curl -sk -H "$AUTH" "https://127.0.0.1/api/session/ext/postgresql/connectionDefaults/3")
echo "$STILL" | grep -q '"domain"' || fail "inheritance lost after save"
echo "PASS: inheritance intact after save"

echo "ALL CHECKS PASSED"

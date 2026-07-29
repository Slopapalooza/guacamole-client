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
# Integration test for inheritable connection group defaults. Exercises the
# REST API of a running deployment: defaults round trip, allowlist
# enforcement, multi-level precedence, and provenance reporting.
#
# Assumes a Docker Compose deployment in ~/guacamole whose environment file
# supplies the administrator password, a PostgreSQL service named "postgres",
# TOTP enabled for the administrator, and connection group 2 plus connection 3
# present. Modifies data - run against a test deployment only.
#
set -u
cd ~/guacamole

PSQL="sudo docker compose --env-file guacamole.env exec -T postgres psql -U guacamole_user -d guacamole_db -tA -c"

fail() { echo "FAIL: $1"; exit 1; }

command -v python3 > /dev/null || fail "python3 not available on VM"

# --- Authenticate as guacadmin (password from env, TOTP from enrolled secret) ---
PW=$(grep '^GUAC_ADMIN_PASSWORD=' guacamole.env | cut -d= -f2- | tr -d '"' | tr -d "'")
PW="${PW%$'\r'}"
[ -n "$PW" ] || fail "could not read admin password"

SECRET=$($PSQL "SELECT ua.attribute_value FROM guacamole_user_attribute ua JOIN guacamole_user u ON u.user_id=ua.user_id JOIN guacamole_entity e ON e.entity_id=u.entity_id WHERE e.name='guacadmin' AND ua.attribute_name='guac-totp-key-secret'")
[ -n "$SECRET" ] || fail "could not read TOTP secret"

# Wait for a fresh TOTP window: a code already used in this window is
# rejected as a replay, which would otherwise break repeated runs
sleep $(( 31 - ($(date +%s) % 30) ))

CODE=$(python3 - "$SECRET" <<'PYEOF'
import base64, hmac, hashlib, struct, time, sys
key = base64.b32decode(sys.argv[1])
counter = int(time.time() // 30)
digest = hmac.new(key, struct.pack('>Q', counter), hashlib.sha1).digest()
offset = digest[19] & 15
code = (struct.unpack('>I', digest[offset:offset+4])[0] & 0x7fffffff) % 1000000
print('%06d' % code)
PYEOF
)
[ -n "$CODE" ] || fail "could not compute TOTP code"

TOKEN=$(curl -sk https://127.0.0.1/api/tokens \
    --data-urlencode "username=guacadmin" \
    --data-urlencode "password=$PW" \
    --data-urlencode "guac-totp=$CODE" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('authToken',''))")
[ -n "$TOKEN" ] || fail "authentication failed (no token)"
echo "PASS: authenticated"

BASE="https://127.0.0.1/api/session/ext/postgresql"
AUTH="Guacamole-Token: $TOKEN"

# --- Reset any state left by a previous run ---
$PSQL "DELETE FROM guacamole_connection_group_parameter" > /dev/null
echo "PASS: cleared prior defaults"

# --- Empty defaults initially ---
OUT=$(curl -sk -H "$AUTH" "$BASE/groupDefaults/2")
[ "$OUT" = "{}" ] || fail "expected empty defaults on group 2, got: $OUT"
echo "PASS: empty defaults on Workstations"

# --- PUT valid defaults ---
HTTP=$(curl -sk -o /tmp/gd-out -w "%{http_code}" -X PUT -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"domain":"example.local","resize-method":"display-update","secondary-monitors":"2"}' \
    "$BASE/groupDefaults/2")
[ "$HTTP" = "200" ] || [ "$HTTP" = "204" ] || fail "PUT defaults returned $HTTP: $(cat /tmp/gd-out)"
echo "PASS: PUT defaults on Workstations ($HTTP)"

# --- Round trip ---
OUT=$(curl -sk -H "$AUTH" "$BASE/groupDefaults/2")
echo "$OUT" | grep -q '"domain":"example.local"' || fail "round trip missing domain: $OUT"
echo "$OUT" | grep -q '"secondary-monitors":"2"' || fail "round trip missing secondary-monitors: $OUT"
echo "PASS: defaults round trip: $OUT"

# --- Allowlist rejection ---
HTTP=$(curl -sk -o /tmp/gd-out -w "%{http_code}" -X PUT -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"password":"nope"}' "$BASE/groupDefaults/2")
[ "$HTTP" = "400" ] || fail "PUT of non-inheritable parameter returned $HTTP (expected 400): $(cat /tmp/gd-out)"
echo "PASS: non-inheritable parameter rejected (400)"

# --- Confirm the rejected PUT did not clobber stored defaults ---
OUT=$(curl -sk -H "$AUTH" "$BASE/groupDefaults/2")
echo "$OUT" | grep -q '"domain":"example.local"' || fail "defaults lost after rejected PUT: $OUT"
echo "PASS: stored defaults intact after rejected PUT"

# --- Nested group: create CSR under Workstations, move connection 3 into it ---
CSR=$($PSQL "SELECT connection_group_id FROM guacamole_connection_group WHERE connection_group_name='CSR'" | head -1 | tr -dc '0-9')
if [ -z "$CSR" ]; then
    $PSQL "INSERT INTO guacamole_connection_group (parent_id, connection_group_name, type) VALUES (2,'CSR','ORGANIZATIONAL')" > /dev/null
    CSR=$($PSQL "SELECT connection_group_id FROM guacamole_connection_group WHERE connection_group_name='CSR'" | head -1 | tr -dc '0-9')
fi
[ -n "$CSR" ] || fail "could not create CSR group"
$PSQL "UPDATE guacamole_connection SET parent_id=$CSR WHERE connection_id=3" > /dev/null
echo "PASS: nested group CSR ($CSR) with connection 3"

# --- Defaults on the nested group ---
HTTP=$(curl -sk -o /tmp/gd-out -w "%{http_code}" -X PUT -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"domain":"csr.local"}' "$BASE/groupDefaults/$CSR")
[ "$HTTP" = "200" ] || [ "$HTTP" = "204" ] || fail "PUT CSR defaults returned $HTTP"
echo "PASS: PUT defaults on CSR"

# --- Inherited-at-group: CSR inherits from Workstations ---
OUT=$(curl -sk -H "$AUTH" "$BASE/groupDefaults/$CSR/inherited")
echo "$OUT" | grep -q '"domain"' || fail "CSR inherited missing domain: $OUT"
echo "$OUT" | grep -q '"sourceName":"Workstations"' || fail "CSR inherited missing provenance: $OUT"
echo "PASS: group inherited endpoint: $OUT"

# --- Connection defaults: nearest ancestor wins, provenance correct ---
HTTP=$(curl -sk -o /tmp/gd-conn -w "%{http_code}" -H "$AUTH" "$BASE/connectionDefaults/3")
echo "connectionDefaults HTTP $HTTP body: $(cat /tmp/gd-conn)"
OUT=$(cat /tmp/gd-conn)
python3 - /tmp/gd-conn <<'PYEOF' || fail "connectionDefaults precedence/provenance wrong"
import sys, json
d = json.load(open(sys.argv[1]))
assert d['domain']['value'] == 'csr.local', d
assert d['domain']['sourceName'] == 'CSR', d
assert d['resize-method']['value'] == 'display-update', d
assert d['resize-method']['sourceName'] == 'Workstations', d
assert d['secondary-monitors']['value'] == '2', d
print('precedence + provenance OK')
PYEOF
echo "PASS: connectionDefaults nearest-wins precedence: $OUT"

echo "ALL CHECKS PASSED"

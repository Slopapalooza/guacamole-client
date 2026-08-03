#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  See the License for the specific language
# governing permissions and limitations under the License.
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# End-to-end verification of the branding API on guac-test.
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
k = base64.b32decode(sys.argv[1])
d = hmac.new(k, struct.pack('>Q', int(time.time() // 30)), hashlib.sha1).digest()
o = d[19] & 15
print('%06d' % ((struct.unpack('>I', d[o:o+4])[0] & 0x7fffffff) % 1000000))
PYEOF
)
TOKEN=$(curl -sk https://127.0.0.1/api/tokens \
  --data-urlencode "username=guacadmin" --data-urlencode "password=$PW" \
  --data-urlencode "guac-totp=$CODE" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('authToken',''))")
[ -n "$TOKEN" ] || fail "authentication failed"
AUTH="Guacamole-Token: $TOKEN"
echo "PASS: authenticated"

# --- site name round trip ---
HTTP=$(curl -sk -o /tmp/b1 -w "%{http_code}" -X PUT -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"siteName":"Pico Test Site"}' https://127.0.0.1/api/branding)
[ "$HTTP" = "200" ] || [ "$HTTP" = "204" ] || fail "PUT siteName -> $HTTP $(cat /tmp/b1)"
curl -sk https://127.0.0.1/api/branding | grep -q '"siteName":"Pico Test Site"' \
  || fail "site name did not round trip"
echo "PASS: site name stored and readable unauthenticated"

# --- unauthenticated write must be rejected ---
HTTP=$(curl -sk -o /dev/null -w "%{http_code}" -X PUT -H "Content-Type: application/json" \
  -d '{"siteName":"hacked"}' https://127.0.0.1/api/branding)
[ "$HTTP" = "403" ] || [ "$HTTP" = "401" ] || [ "$HTTP" = "500" ] || fail "unauthenticated write returned $HTTP"
curl -sk https://127.0.0.1/api/branding | grep -q '"siteName":"Pico Test Site"' \
  || fail "unauthenticated write CHANGED the site name"
echo "PASS: unauthenticated write rejected ($HTTP), value unchanged"

# --- valid PNG upload ---
python3 -c "
import struct, zlib, io
def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n'
png += chunk(b'IHDR', struct.pack('>IIBBBBB', 8, 8, 8, 2, 0, 0, 0))
raw = b''.join(b'\x00' + b'\x40\x60\xa0' * 8 for _ in range(8))
png += chunk(b'IDAT', zlib.compress(raw))
png += chunk(b'IEND', b'')
open('/tmp/logo.png','wb').write(png)
"
HTTP=$(curl -sk -o /tmp/b2 -w "%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/octet-stream" --data-binary @/tmp/logo.png \
  https://127.0.0.1/api/branding/logo)
[ "$HTTP" = "200" ] || [ "$HTTP" = "204" ] || fail "PNG upload -> $HTTP $(cat /tmp/b2)"
echo "PASS: valid PNG accepted"

curl -sk https://127.0.0.1/api/branding | grep -q '"hasLogo":true' || fail "hasLogo not set"
TYPE=$(curl -sk -o /dev/null -w "%{content_type}" https://127.0.0.1/api/branding/logo)
echo "$TYPE" | grep -q "image/png" || fail "logo served as $TYPE"
echo "PASS: logo served unauthenticated as $TYPE"
curl -skI https://127.0.0.1/api/branding/logo | grep -qi "nosniff" && echo "PASS: nosniff header present"

# --- a non-image with an image name must be rejected ---
printf '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>' > /tmp/evil.png
HTTP=$(curl -sk -o /tmp/b3 -w "%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/octet-stream" --data-binary @/tmp/evil.png \
  https://127.0.0.1/api/branding/logo)
[ "$HTTP" = "400" ] || fail "SVG/script payload returned $HTTP (expected 400): $(cat /tmp/b3)"
echo "PASS: script-bearing SVG rejected (400)"
TYPE=$(curl -sk -o /dev/null -w "%{content_type}" https://127.0.0.1/api/branding/logo)
echo "$TYPE" | grep -q "image/png" || fail "rejected upload replaced the stored logo"
echo "PASS: stored logo untouched by the rejected upload"

# --- oversize rejected ---
head -c 300000 /dev/urandom > /tmp/big.bin
python3 -c "
d=open('/tmp/big.bin','rb').read()
open('/tmp/big.png','wb').write(b'\x89PNG\r\n\x1a\n'+d)
"
HTTP=$(curl -sk -o /dev/null -w "%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/octet-stream" --data-binary @/tmp/big.png \
  https://127.0.0.1/api/branding/logo)
[ "$HTTP" = "400" ] || [ "$HTTP" = "413" ] || fail "oversize upload returned $HTTP"
echo "PASS: oversize upload rejected ($HTTP)"

# --- cleanup ---
curl -sk -X DELETE -H "$AUTH" https://127.0.0.1/api/branding/logo > /dev/null
curl -sk -X PUT -H "$AUTH" -H "Content-Type: application/json" -d '{"siteName":null}' \
  https://127.0.0.1/api/branding > /dev/null
curl -sk https://127.0.0.1/api/branding | grep -q '"hasLogo":false' || fail "logo not removed"
echo "PASS: logo removed and site name cleared"
echo "ALL CHECKS PASSED"

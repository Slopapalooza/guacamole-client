# Connection Group Defaults

Connection groups can store default parameter values which apply to every
connection beneath them. A connection's own value always wins; where it has
none, the value of the nearest ancestor group defining that parameter is used.
Nothing is copied into connections — resolution happens each time a connection
is opened, so editing a group's defaults immediately affects every descendant.

## Using it

Edit a connection group as an administrator and fill in the **Connection
Defaults** section. Blank means "no default". Values already arriving from
parent groups are listed above the form.

On a connection's edit page, parameters supplied by an ancestor are annotated
beneath the field (*Inherited from CSR: example.local*). Typing a value there
overrides the default, which is then annotated as an override with a **Use
default** action that clears it again. Moving a connection to a different group
warns which inherited values the move would change before it is saved.

## Database

Defaults live in `guacamole_connection_group_parameter`, added by
`schema/upgrade/upgrade-pico-001-group-parameters.sql`.

**There is no automatic migration.** Apply that script by hand to existing
databases, and remember it for fresh installs and disaster-recovery rebuilds —
without the table, startup succeeds but every defaults request fails.

## Which parameters may be inherited

Enforced by an allowlist in `GroupDefaultsService.INHERITABLE_PARAMETERS`,
checked when defaults are saved and again when they are applied, so rows
inserted directly into the database cannot widen it.

Inheritable: `domain`; the server-verification settings `security`,
`ignore-cert`, `cert-tofu`; and display/performance settings (`resize-method`,
`secondary-monitors`, `color-depth`, `force-lossless`, `dpi`, `server-layout`,
`timezone`, and the `enable-*`/`disable-*` rendering flags).

Permanently excluded, and not to be added:

| Class | Why |
|---|---|
| Credentials (`password`, `username`, `private-key`, `passphrase`, `sftp-*`, `gateway-*`) | Descendants would reach systems using credentials nobody can see on the connection itself |
| Filesystem paths (`recording-path`, `drive-path`, `sftp-root-directory`) | Affects the guacd host's filesystem for every descendant at once |
| Execution and redirection (`initial-program`, `remote-app`, `enable-drive`) | Changes what runs, or what is exposed, in every descendant session |
| Addressing (`hostname`, `port`) | Every descendant silently pointing at one host is never what was meant |

### Server verification is inheritable, and that cuts both ways

Managing certificate trust per folder is a main reason to have defaults at all,
but one group edit can relax verification for every descendant — including
connections created later, whose edit pages never mention it. Mitigations:

- Only administrators can read or write defaults.
- Every connection's edit page annotates inherited values with their source.
- Applying an inherited `security`, `ignore-cert`, or `cert-tofu` is logged at
  `info`, naming the group, so *"why did this session skip verification"* is
  answerable from logs alone.

## Permissions

Reading and writing group defaults requires system administration privileges.
Reading the inherited values applicable to one connection requires UPDATE on
that connection, mirroring the gate on reading its parameters. Ordinary users
see nothing: their sessions are simply configured correctly.

## Limits and interactions

- **Explicit blank cannot override an inherited value.** Clearing a field means
  "inherit"; there is no way to say "inherit nothing here". Move the connection
  or drop the group default instead.
- **Sharing profiles are unaffected.** Joining a shared session uses the
  sharing profile's parameters, which are access restrictions rather than
  connection configuration.
- **Only connections stored in this extension inherit.** Connections defined by
  other authentication extensions never pass through this code.
- **Imported connections override.** The CSV/JSON import writes a complete
  parameter set per connection, so imported connections carry their own values
  and inherit nothing until those values are removed.

## Testing

`src/test/scripts/validate-group-defaults.sh` covers the API: round trip,
allowlist rejection, multi-level precedence, and provenance.
`src/test/scripts/validate-no-materialization.sh` covers the invariant that
saving a connection which inherits values must not copy them into the
connection — the failure that would silently sever inheritance.

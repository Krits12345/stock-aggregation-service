# config/

Settings and the Cassandra connection. The `*Properties` classes are typed config
bound from `application.yml`, and every value can be overridden with an environment
variable (handy for Docker).

- **CassandraProperties.java** - hosts, port, keyspace, datacenter, optional
  username/password.
- **CacheProperties.java** - cache TTL and max size.
- **JwtProperties.java** - JWT secret and token lifetime.
- **AppProperties.java** - app-level flags, currently the `require-auth` toggle.
- **LoaderProperties.java** - where the loader finds the schema file and the CSV.
- **CassandraSession.java** - owns the single `CqlSession` everything shares. It
  connects lazily on first use instead of at startup, so the app still boots when
  Cassandra is briefly down (and `/health` then reports the truth). It connects with
  no default keyspace; the repositories use keyspace-qualified statements.
- **OpenApiConfig.java** - title and description for the Swagger UI at `/apidocs`.

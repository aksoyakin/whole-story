# GeoIP test fixture

`GeoIP2-City-Test.mmdb` is MaxMind's own test database from
[maxmind/MaxMind-DB](https://github.com/maxmind/MaxMind-DB) (`test-data/`), which is published under the
Apache License 2.0 / MIT (Copyright (c) 2013 - 2026 by MaxMind, Inc.). It contains fabricated records only.

It is committed so that geo resolution is covered by deterministic tests without a MaxMind licence key.
The real GeoLite2 database is never committed (see `.gitignore`).

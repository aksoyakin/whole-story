# Country borders

`countries-110m.json` is the TopoJSON the dashboard's map draws, copied from the
[world-atlas](https://github.com/topojson/world-atlas) package (ISC), which builds it from
[Natural Earth](https://www.naturalearthdata.com) — public domain.

It is committed rather than copied in at build time: it is reference data that changes about as often as a border
does, and having it here keeps development and production identical with nothing in between.

Regenerate it, together with the numeric-to-alpha-2 table the map needs, with:

```bash
node scripts/generate-map-data.mjs
```

The countries are keyed by numeric ISO 3166-1 codes while everything this product stores is alpha-2, which is why
that table exists. Three territories in the file have no ISO code at all — Northern Cyprus, Somaliland and
Kosovo — and are drawn as having no visitors, which is all this product can say about them.

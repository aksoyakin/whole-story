# Referrer source database

`referers.yml` is the referrer database from
[snowplow-referer-parser/referer-parser](https://github.com/snowplow-referer-parser/referer-parser).

It is based on Matomo's `SearchEngines.php` and `Socials.php`, copyright 2012 Matthieu Aubry, and is available
under the **GNU General Public License v3**. Whole Story is therefore distributed under the GPL v3 as well; the
licence text is in `LICENSE` at the root of this repository.

Only the `domains` of each provider are used, to map a referrer onto a source name. The `parameters` entries,
which exist to extract search keywords from the referrer URL, are deliberately ignored: this product does not
collect what people searched for.

To update, replace the file with the current version from the upstream repository.

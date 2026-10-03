/**
 * Deleting the analytics data of a removed site. The processor owns the schema, so it is the only service that
 * can; Site Management tells it which site through the {@code site-purge} topic.
 */
package world.wholestory.processor.purge;

/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.automaticindexing.filter.impl;

import java.util.Set;

import org.hibernate.search.mapper.pojo.automaticindexing.filter.spi.ConfiguredSearchIndexingPlanFilter;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeIdentifier;

public abstract class HibernateOrmConfiguredSearchIndexingPlanFilter implements ConfiguredSearchIndexingPlanFilter {

	public static HibernateOrmConfiguredSearchIndexingPlanFilter create(
			Set<PojoRawTypeIdentifier<?>> includes,
			Set<PojoRawTypeIdentifier<?>> excludes
	) {
		if ( includes.isEmpty() ) {
			return ExcludeAll.INSTANCE;
		}
		if ( excludes.isEmpty() ) {
			return IncludeAll.INSTANCE;
		}
		return new FilterNoFallback( excludes );
	}

	public abstract boolean supportsEventQueue();

	public static class IncludeAll extends HibernateOrmConfiguredSearchIndexingPlanFilter {

		public static IncludeAll INSTANCE = new IncludeAll();

		@Override
		public boolean isIncluded(PojoRawTypeIdentifier<?> typeIdentifier) {
			return true;
		}

		@Override
		public boolean supportsEventQueue() {
			// cannot work with outbox polling since this filter would allow for all events to be persisted,
			// but then an application level filter might think otherwise when the events will be processed.
			return false;
		}
	}

	private static class ExcludeAll extends HibernateOrmConfiguredSearchIndexingPlanFilter {

		static ExcludeAll INSTANCE = new ExcludeAll();

		@Override
		public boolean isIncluded(PojoRawTypeIdentifier<?> typeIdentifier) {
			return false;
		}

		@Override
		public boolean supportsEventQueue() {
			return true;
		}
	}

	private static class FilterNoFallback extends HibernateOrmConfiguredSearchIndexingPlanFilter {
		protected final Set<PojoRawTypeIdentifier<?>> excludes;

		private FilterNoFallback(Set<PojoRawTypeIdentifier<?>> excludes) {
			this.excludes = excludes;
		}

		@Override
		public boolean isIncluded(PojoRawTypeIdentifier<?> typeIdentifier) {
			return !excludes.contains( typeIdentifier );
		}

		public boolean supportsEventQueue() {
			return false;
		}

	}
}
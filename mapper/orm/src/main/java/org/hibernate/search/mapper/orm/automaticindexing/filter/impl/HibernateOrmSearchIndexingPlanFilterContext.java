/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.automaticindexing.filter.impl;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.hibernate.search.mapper.orm.logging.impl.Log;
import org.hibernate.search.mapper.orm.session.impl.HibernateOrmAutomaticIndexingTypeFilterTypeContextProvider;
import org.hibernate.search.mapper.pojo.automaticindexing.filter.SearchIndexingPlanFilterContext;
import org.hibernate.search.mapper.pojo.automaticindexing.filter.spi.ConfiguredSearchIndexingPlanFilter;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeIdentifier;
import org.hibernate.search.mapper.pojo.model.spi.PojoTypeContext;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

public class HibernateOrmSearchIndexingPlanFilterContext implements SearchIndexingPlanFilterContext {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final HibernateOrmAutomaticIndexingTypeFilterTypeContextProvider contextProvider;

	private final Set<PojoRawTypeIdentifier<?>> includes = new HashSet<>();
	private final Set<PojoRawTypeIdentifier<?>> excludes = new HashSet<>();

	public HibernateOrmSearchIndexingPlanFilterContext(
			HibernateOrmAutomaticIndexingTypeFilterTypeContextProvider typeManager) {
		this.contextProvider = typeManager;
	}

	@Override
	public SearchIndexingPlanFilterContext include(String name) {
		addIfNotPresentInOther(
				contextProvider.byEntityName().getOrFail( name ).typeIdentifier(),
				includes,
				excludes
		);
		return this;
	}

	@Override
	public SearchIndexingPlanFilterContext include(Class<?> clazz) {
		addIfNotPresentInOther(
				contextProvider.indexedWithSuperTypesByExactClass().getOrFail( clazz ),
				includes,
				excludes
		);
		return this;
	}

	@Override
	public SearchIndexingPlanFilterContext exclude(String name) {
		addIfNotPresentInOther(
				contextProvider.byEntityName().getOrFail( name ).typeIdentifier(),
				excludes,
				includes
		);
		return this;
	}

	@Override
	public SearchIndexingPlanFilterContext exclude(Class<?> clazz) {
		addIfNotPresentInOther(
				contextProvider.indexedWithSuperTypesByExactClass().getOrFail( clazz ),
				excludes,
				includes
		);
		return this;
	}

	public HibernateOrmConfiguredSearchIndexingPlanFilter createFilter() {
		return createFilter( null );
	}

	public HibernateOrmConfiguredSearchIndexingPlanFilter createFilter(ConfiguredSearchIndexingPlanFilter fallback) {
		Set<PojoRawTypeIdentifier<?>> allIncludes = new HashSet<>();
		Set<PojoRawTypeIdentifier<?>> allExcludes = new HashSet<>();
		boolean allTypesProcessed = true;

		for ( PojoTypeContext<?> typeContext : contextProvider.byEntityName().values() ) {
			PojoRawTypeIdentifier<?> typedIdentifier = typeContext.typeIdentifier();

			//.include(...) => included no matter what; subclasses included unless excluded explicitly in the same filter
			//.exclude(...) => excluded no matter what; subclasses excluded unless included explicitly in the same filter
			//.include(...) + .exclude(...) for the same entity in the same filter => exception
			// neither .include(...) nor .exclude(...) => defer to the superclass inclusions/exclusions,
			// then to the application filter, with the same behavior, and by default include
			if ( excludes.contains( typedIdentifier ) ) {
				allExcludes.add( typedIdentifier );
			}
			else if ( includes.contains( typedIdentifier ) ) {
				allIncludes.add( typedIdentifier );
			}
			else {
				Optional<Class<?>> closestInclude = findClosestClass( typedIdentifier.javaClass(), includes );
				Optional<Class<?>> closestExclude = findClosestClass( typedIdentifier.javaClass(), excludes );

				if ( closestInclude.isPresent() && closestExclude.isPresent() ) {
					// if include is a subclass of exclude - then include is more specific, and we allow indexing:
					if ( closestExclude.get().isAssignableFrom( closestInclude.get() ) ) {
						allIncludes.add( typedIdentifier );
					}
					else {
						allExcludes.add( typedIdentifier );
					}
				}
				else if ( closestExclude.isPresent() ) {
					allExcludes.add( typedIdentifier );
				}
				else if ( closestInclude.isPresent() ) {
					allIncludes.add( typedIdentifier );
				}
				else {
					if ( fallback == null ) {
						// types are included by default
						allIncludes.add( typedIdentifier );
					}
					else {
						if ( fallback.isIncluded( typedIdentifier ) ) {
							allIncludes.add( typedIdentifier );
						}
						else {
							allExcludes.add( typedIdentifier );
						}
					}
				}
			}
		}

		return HibernateOrmConfiguredSearchIndexingPlanFilter.create(
				Collections.unmodifiableSet( allIncludes ),
				Collections.unmodifiableSet( allExcludes )
		);
	}

	private Optional<Class<?>> findClosestClass(Class<?> current, Set<PojoRawTypeIdentifier<?>> collection) {
		Class<?> closest = Object.class;
		for ( PojoRawTypeIdentifier<?> identifier : collection ) {
			if ( identifier.javaClass().isAssignableFrom( current ) ) {
				closest = identifier.javaClass().isAssignableFrom( closest ) ? closest : identifier.javaClass();
			}
		}

		if ( Object.class.equals( closest ) ) {
			return Optional.empty();
		}
		else {
			return Optional.of( closest );
		}
	}

	private boolean addIfNotPresentInOther(PojoRawTypeIdentifier<?> typeIdentifier, Set<PojoRawTypeIdentifier<?>> a,
			Set<PojoRawTypeIdentifier<?>> b) {
		if ( b.contains( typeIdentifier ) ) {
			throw log.automaticIndexingFilterCannotIncludeExcludeSameType( typeIdentifier, includes, excludes );
		}
		return a.add( typeIdentifier );
	}
}
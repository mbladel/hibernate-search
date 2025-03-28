/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.types.dsl.provider.impl;

import org.hibernate.search.backend.elasticsearch.logging.impl.VersionLog;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;
import org.hibernate.search.backend.elasticsearch.types.impl.ElasticsearchIndexValueFieldType;
import org.hibernate.search.backend.elasticsearch.types.mapping.impl.ElasticsearchVectorFieldTypeMappingContributor;

import com.google.gson.Gson;

/**
 * The index field type factory provider for ES7.x.
 */
public class Elasticsearch7IndexFieldTypeFactoryProvider extends AbstractIndexFieldTypeFactoryProvider {

	private final ElasticsearchVectorFieldTypeMappingContributor vectorFieldTypeMappingContributor =
			new ElasticsearchVectorFieldTypeMappingContributor() {

				@Override
				public void contribute(PropertyMapping mapping, Context context) {
					throw VersionLog.INSTANCE.searchBackendVersionIncompatibleWithVectorIntegration( "Elasticsearch", "8.12" );
				}

				@Override
				public <F> void contribute(ElasticsearchIndexValueFieldType.Builder<F> builder, Context context) {
					throw VersionLog.INSTANCE.searchBackendVersionIncompatibleWithVectorIntegration( "Elasticsearch", "8.12" );
				}
			};

	public Elasticsearch7IndexFieldTypeFactoryProvider(Gson userFacingGson) {
		super( userFacingGson );
	}

	@Override
	protected ElasticsearchVectorFieldTypeMappingContributor vectorFieldTypeMappingContributor() {
		return vectorFieldTypeMappingContributor;
	}
}

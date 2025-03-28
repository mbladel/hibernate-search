/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.impl;

import java.util.Optional;

import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.mapper.pojo.logging.impl.MappingLog;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionBinding;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MappingAnnotationProcessorContext;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MethodParameterMappingAnnotationProcessor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MethodParameterMappingAnnotationProcessorContext;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.MethodParameterMappingStep;
import org.hibernate.search.mapper.pojo.search.definition.binding.ProjectionBinder;
import org.hibernate.search.mapper.pojo.search.definition.mapping.annotation.ProjectionBinderRef;

public final class ProjectionBindingProcessor
		implements MethodParameterMappingAnnotationProcessor<ProjectionBinding> {

	@Override
	public void process(MethodParameterMappingStep mapping, ProjectionBinding annotation,
			MethodParameterMappingAnnotationProcessorContext context) {
		ProjectionBinderRef referenceAnnotation = annotation.binder();
		mapping.projection( createBinderReference( referenceAnnotation, context ),
				context.toMap( referenceAnnotation.params() ) );
	}

	private BeanReference<? extends ProjectionBinder> createBinderReference(ProjectionBinderRef referenceAnnotation,
			MappingAnnotationProcessorContext context) {
		Optional<BeanReference<? extends ProjectionBinder>> reference = context.toBeanReference(
				ProjectionBinder.class,
				ProjectionBinderRef.UndefinedImplementationType.class,
				referenceAnnotation.type(), referenceAnnotation.name(),
				referenceAnnotation.retrieval()
		);

		if ( !reference.isPresent() ) {
			throw MappingLog.INSTANCE.missingBinderReferenceInBinding();
		}

		return reference.get();
	}
}

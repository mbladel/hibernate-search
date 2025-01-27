/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.massindexing.impl;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import org.hibernate.search.mapper.pojo.loading.spi.PojoLoadingTypeContext;
import org.hibernate.search.mapper.pojo.loading.spi.PojoMassLoadingStrategy;
import org.hibernate.search.mapper.pojo.logging.impl.Log;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingEnvironment;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingType;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingTypeGroupMonitor;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingTypeGroupMonitorContext;
import org.hibernate.search.mapper.pojo.massindexing.spi.PojoMassIndexingContext;
import org.hibernate.search.mapper.pojo.massindexing.spi.PojoMassIndexingMappingContext;
import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.common.impl.Futures;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

/**
 * This runnable will prepare a pipeline for batch indexing
 * of entities, managing the lifecycle of several ThreadPools.
 *
 * @author Sanne Grinovero
 * @param <E> The type of indexed entities.
 * @param <I> The type of identifiers.
 */
public class PojoMassIndexingBatchIndexingWorkspace<E, I> extends PojoMassIndexingFailureHandledRunnable {

	public static final String THREAD_NAME_PREFIX = "Mass indexing - ";

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final List<CompletableFuture<?>> identifierProducingFutures = new ArrayList<>();
	private final List<CompletableFuture<?>> indexingFutures = new ArrayList<>();
	private final PojoMassIndexingMappingContext mappingContext;
	private final PojoMassIndexingIndexedTypeGroup<E> typeGroup;
	private final PojoMassLoadingStrategy<E, I> loadingStrategy;
	private final PojoMassIndexingContext massIndexingContext;

	private final int entityExtractingThreads;
	private final String tenantId;
	private final MassIndexingTypeGroupMonitor typeGroupMonitor;

	PojoMassIndexingBatchIndexingWorkspace(PojoMassIndexingMappingContext mappingContext,
			PojoMassIndexingNotifier notifier,
			MassIndexingEnvironment environment,
			PojoMassIndexingIndexedTypeGroup<E> typeGroup,
			PojoMassLoadingStrategy<E, I> loadingStrategy,
			PojoMassIndexingContext massIndexingContext,
			int entityExtractingThreads, String tenantId) {
		super( notifier, environment );
		this.mappingContext = mappingContext;
		this.typeGroup = typeGroup;
		this.loadingStrategy = loadingStrategy;
		this.massIndexingContext = massIndexingContext;
		this.entityExtractingThreads = entityExtractingThreads;
		this.tenantId = tenantId;
		this.typeGroupMonitor = notifier.typeGroupMonitor( new MassIndexingTypeGroupMonitorContextImpl( typeGroup ) );
	}

	@Override
	public void runWithFailureHandler() throws InterruptedException {
		if ( !identifierProducingFutures.isEmpty() || !indexingFutures.isEmpty() ) {
			throw new AssertionFailure( "BatchIndexingWorkspace instance not expected to be reused" );
		}

		PojoProducerConsumerQueue<List<I>> identifierQueue = new PojoProducerConsumerQueue<>( 1 );

		// First start the consumers, then the producers (reverse order):
		startIndexing( identifierQueue );
		startProducingPrimaryKeys( identifierQueue );
		// Wait for indexing to finish.
		List<CompletableFuture<?>> allFutures = new ArrayList<>();
		allFutures.addAll( identifierProducingFutures );
		allFutures.addAll( indexingFutures );
		Futures.unwrappedExceptionGet( Futures.firstFailureOrAllOf( allFutures ) );
		typeGroupMonitor.indexingCompleted();
		log.debugf( "Indexing for %s is done", typeGroup.notifiedGroupName() );
	}

	@Override
	protected void cleanUpOnInterruption() {
		cancelPendingTasks();
	}

	@Override
	protected void cleanUpOnFailure() {
		cancelPendingTasks();
	}

	private void cancelPendingTasks() {
		// Cancel each pending task - threads executing the tasks must be interrupted
		for ( Future<?> task : identifierProducingFutures ) {
			task.cancel( true );
		}
		for ( Future<?> task : indexingFutures ) {
			task.cancel( true );
		}
	}

	private void startProducingPrimaryKeys(PojoProducerConsumerQueue<List<I>> identifierQueue) {
		final Runnable runnable = new PojoMassIndexingEntityIdentifierLoadingRunnable<>(
				getNotifier(),
				typeGroupMonitor,
				massIndexingContext, getMassIndexingEnvironment(),
				typeGroup, loadingStrategy,
				identifierQueue, tenantId
		);
		//execIdentifiersLoader has size 1 and is not configurable: ensures the list is consistent as produced by one transaction
		final ThreadPoolExecutor identifierProducingExecutor = mappingContext.threadPoolProvider().newFixedThreadPool(
				1,
				THREAD_NAME_PREFIX + typeGroup.notifiedGroupName() + " - ID loading"
		);
		try {
			identifierProducingFutures.add( Futures.runAsync( runnable, identifierProducingExecutor ) );
		}
		finally {
			identifierProducingExecutor.shutdown();
		}
	}

	private void startIndexing(PojoProducerConsumerQueue<List<I>> identifierQueue) {
		final Runnable runnable = new PojoMassIndexingEntityLoadingRunnable<>(
				getNotifier(),
				typeGroupMonitor,
				massIndexingContext, getMassIndexingEnvironment(),
				typeGroup, loadingStrategy,
				identifierQueue, tenantId
		);
		final ThreadPoolExecutor indexingExecutor = mappingContext.threadPoolProvider().newFixedThreadPool(
				entityExtractingThreads,
				THREAD_NAME_PREFIX + typeGroup.notifiedGroupName() + " - Entity loading"
		);
		try {
			for ( int i = 0; i < entityExtractingThreads; i++ ) {
				indexingFutures.add( Futures.runAsync( runnable, indexingExecutor ) );
			}
		}
		finally {
			indexingExecutor.shutdown();
		}
	}

	private static class MassIndexingTypeGroupMonitorContextImpl implements MassIndexingTypeGroupMonitorContext {

		private final Set<MassIndexingType> includedTypes;

		public MassIndexingTypeGroupMonitorContextImpl(PojoMassIndexingIndexedTypeGroup<?> typeGroup) {
			includedTypes = typeGroup.includedTypes().stream().map( PojoLoadingTypeContext::entityName )
					.map( MassIndexingTypeImpl::new )
					.collect( Collectors.toSet() );
		}

		@Override
		public Set<MassIndexingType> includedTypes() {
			return includedTypes;
		}
	}


	private static class MassIndexingTypeImpl implements MassIndexingType {
		private final String entityName;

		private MassIndexingTypeImpl(String entityName) {
			this.entityName = entityName;
		}

		@Override
		public String entityName() {
			return entityName;
		}
	}
}

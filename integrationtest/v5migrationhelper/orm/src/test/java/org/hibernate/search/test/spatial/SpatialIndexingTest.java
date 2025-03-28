/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.test.spatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.hibernate.Transaction;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.annotations.Spatial;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.dsl.Unit;
import org.hibernate.search.test.SearchTestBase;
import org.hibernate.search.testsupport.TestForIssue;
import org.hibernate.search.testsupport.junit.Tags;
import org.hibernate.search.util.common.SearchException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.apache.lucene.search.Sort;

/**
 * Hibernate Search spatial : unit tests on indexing POIs in with Grid and Grid+Distance
 *
 * @author Nicolas Helleringer
 * @author Hardy Ferentschik
 */
class SpatialIndexingTest extends SearchTestBase {
	private FullTextSession fullTextSession;

	@BeforeEach
	void createAndIndexTestData() throws Exception {
		fullTextSession = Search.getFullTextSession( openSession() );

		Transaction tx = fullTextSession.beginTransaction();

		// POI
		fullTextSession.persist( new POI( 1, "Distance to 24,32 : 0", 24.0d, 32.0d, "" ) );
		fullTextSession.persist( new POI( 2, "Distance to 24,32 : 10.16", 24.0d, 31.9d, "" ) );
		fullTextSession.persist( new POI( 3, "Distance to 24,32 : 11.12", 23.9d, 32.0d, "" ) );
		fullTextSession.persist( new POI( 4, "Distance to 24,32 : 15.06", 23.9d, 32.1d, "" ) );
		fullTextSession.persist( new POI( 5, "Distance to 24,32 : 22.24", 24.2d, 32.0d, "" ) );
		fullTextSession.persist( new POI( 6, "Distance to 24,32 : 24.45", 24.2d, 31.9d, "" ) );

		// NonGeoPOI
		fullTextSession.persist( new NonGeoPOI( 1, "Distance to 24,32 : 0", 24.0d, null, "" ) );
		fullTextSession.persist( new NonGeoPOI( 2, "Distance to 24,32 : 24.45", 24.2d, 31.9d, "" ) );
		fullTextSession.persist( new NonGeoPOI( 3, "Distance to 24,32 : 10.16", 24.0d, 31.9d, "" ) );
		fullTextSession.persist( new NonGeoPOI( 4, "Distance to 24,32 : 15.06", 23.9d, 32.1d, "" ) );
		fullTextSession.persist( new NonGeoPOI( 5, "Distance to 24,32 : 11.12", 23.9d, 32.0d, "" ) );
		fullTextSession.persist( new NonGeoPOI( 6, "Distance to 24,32 : 22.24", 24.2d, 32.0d, "" ) );

		// MissingSpatialPOI
		fullTextSession.persist( new MissingSpatialPOI( 1, "Distance to 24,32 : 0", 24.0d, 32.0d, "" ) );

		// Event
		SimpleDateFormat dateFormat = new SimpleDateFormat( "d M yyyy", Locale.ROOT );
		Date date = dateFormat.parse( "10 9 1976" );
		fullTextSession.persist( new Event( 1, "Test", 24.0d, 32.0d, date ) );

		// User
		fullTextSession.persist( new User( 1, 24.0d, 32.0d ) );

		// UserRange
		fullTextSession.persist( new UserRange( 1, 24.0d, 32.0d ) );

		// UserEx
		fullTextSession.persist( new UserEx( 1, 24.0d, 32.0d, 11.9d, 27.4d ) );

		// RangeEvent
		dateFormat = new SimpleDateFormat( "d M yyyy", Locale.ROOT );
		date = dateFormat.parse( "10 9 1976" );
		fullTextSession.persist( new RangeEvent( 1, "Test", 24.0d, 32.0d, date ) );

		// Hotel
		fullTextSession.persist( new Hotel( 1, "Plazza Athénée", 24.0d, 32.0d, "Luxurious" ) );

		// RangeHotel
		fullTextSession.persist( new RangeHotel( 1, "Plazza Athénée", 24.0d, 32.0d, "Luxurious" ) );
		fullTextSession.persist( new RangeHotel( 2, "End of the world Hotel - Left", 0.0d, 179.0d, "Roots" ) );
		fullTextSession.persist( new RangeHotel( 3, "End of the world Hotel - Right", 0.0d, -179.0d, "Cosy" ) );

		// Restaurant
		fullTextSession.persist(
				new Restaurant( 1, "Al's kitchen", "42, space avenue CA8596 BYOB Street", 24.0d, 32.0d )
		);

		// GetterUser
		fullTextSession.persist( new GetterUser( 1, 24.0d, 32.0d ) );

		//DoubleIndexedPOIs
		fullTextSession
				.persist( new DoubleIndexedPOI( 1, "Davide D'Alto", 37.780392d, -122.513898d, "Hibernate team member" ) );
		fullTextSession.persist( new DoubleIndexedPOI( 2, "Peter O'Tall", 40.723165d, -73.987439d, "" ) );

		tx.commit();
	}

	@Test
	@Tag(Tags.SKIP_ON_ELASTICSEARCH)
	// Elasticsearch does not support a radius of 0 (starting from 2.2.0)
	void testIndexingRadius0() throws Exception {
		double centerLatitude = 24;
		double centerLongitude = 32;

		assertNumberOfPointsOfInterestWithinRadius( centerLatitude, centerLongitude, 0, 1 );
	}

	@Test
	void testIndexing() throws Exception {
		double centerLatitude = 24;
		double centerLongitude = 32;

		assertNumberOfPointsOfInterestWithinRadius( centerLatitude, centerLongitude, 10, 1 );
		assertNumberOfPointsOfInterestWithinRadius( centerLatitude, centerLongitude, 20, 4 );
		assertNumberOfPointsOfInterestWithinRadius( centerLatitude, centerLongitude, 30, 6 );
	}

	@Test
	void testDistanceProjection() throws Exception {
		double centerLatitude = 24.0d;
		double centerLongitude = 32.0d;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( POI.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder
				.spatial()
				.onField( "location" )
				.within( 100, Unit.KM )
				.ofLatitude( centerLatitude )
				.andLongitude( centerLongitude )
				.createQuery();

		FullTextQuery hibQuery = fullTextSession.createFullTextQuery( luceneQuery, POI.class );
		hibQuery.setProjection( FullTextQuery.THIS, FullTextQuery.SPATIAL_DISTANCE );
		hibQuery.setSpatialParameters( centerLatitude, centerLongitude, "location" );
		hibQuery.setSort( builder.sort().byDistance().onField( "location" ).fromLatitude( centerLatitude )
				.andLongitude( centerLongitude ).createSort() );
		List results = hibQuery.list();
		Object[] firstResult = (Object[]) results.get( 0 );
		Object[] secondResult = (Object[]) results.get( 1 );
		Object[] thirdResult = (Object[]) results.get( 2 );
		Object[] fourthResult = (Object[]) results.get( 3 );
		Object[] fifthResult = (Object[]) results.get( 4 );
		Object[] sixthResult = (Object[]) results.get( 5 );
		assertThat( ( (Double) firstResult[1] ) ).isEqualTo( 0.0, within( 0.0001 ) );
		assertThat( ( (Double) secondResult[1] ) ).isEqualTo( 10.1582, within( 0.01 ) );
		assertThat( ( (Double) thirdResult[1] ) ).isEqualTo( 11.1195, within( 0.01 ) );
		assertThat( ( (Double) fourthResult[1] ) ).isEqualTo( 15.0636, within( 0.01 ) );
		assertThat( ( (Double) fifthResult[1] ) ).isEqualTo( 22.239, within( 0.02 ) );
		assertThat( ( (Double) sixthResult[1] ) ).isEqualTo( 24.446, within( 0.02 ) );
	}

	@Test
	void testDistanceSort() throws Exception {
		double centerLatitude = 24.0d;
		double centerLongitude = 32.0d;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( POI.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "location" )
				.within( 100, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		FullTextQuery hibQuery = fullTextSession.createFullTextQuery( luceneQuery, POI.class );
		Sort distanceSort = builder.sort().byDistance().onField( "location" )
				.fromLatitude( centerLatitude ).andLongitude( centerLongitude )
				.createSort();
		hibQuery.setSort( distanceSort );
		hibQuery.setProjection( FullTextQuery.THIS, FullTextQuery.SPATIAL_DISTANCE );
		hibQuery.setSpatialParameters( centerLatitude, centerLongitude, "location" );
		List<Object[]> results = hibQuery.list();

		Double previousDistance = (Double) results.get( 0 )[1];
		for ( int i = 1; i < results.size(); i++ ) {
			Object[] projectionEntry = results.get( i );
			Double currentDistance = (Double) projectionEntry[1];
			assertThat( previousDistance ).isLessThan( currentDistance )
					.as( previousDistance + " should be < " + currentDistance );
			previousDistance = currentDistance;
		}
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-1708")
	void testNonGeoDistanceSortOnNonSpatialField() throws Exception {
		double centerLatitude = 24.0d;
		double centerLongitude = 32.0d;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( NonGeoPOI.class ).get();

		try {
			builder.sort().byDistance().onField( "name" )
					.fromLatitude( centerLatitude ).andLongitude( centerLongitude )
					.createSort();
			fail( "Sorting on a field that it is not a coordinate should fail" );
		}
		catch (SearchException e) {
			assertThat( e ).hasMessageContaining( "Cannot use 'sort:distance' on field 'name'" );
		}
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-1708")
	void testNonGeoDistanceSortOnMissingField() throws Exception {
		double centerLatitude = 24.0d;
		double centerLongitude = 32.0d;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( NonGeoPOI.class ).get();

		try {
			builder.sort().byDistance().onField( "location" )
					.fromLatitude( centerLatitude ).andLongitude( centerLongitude )
					.createSort();
			fail( "Sorting on a field not indexed should fail" );
		}
		catch (SearchException e) {
			assertThat( e ).hasMessageContaining( "Unknown field 'location'" );
		}
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-1470")
	void testSpatialQueryOnNonSpatialConfiguredEntityThrowsException() {
		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( MissingSpatialPOI.class ).get();

		try {
			builder.spatial()
					.within( 1, Unit.KM )
					.ofLatitude( 0d )
					.andLongitude( 0d )
					.createQuery();
			fail( "Building an invalid spatial query should fail" );
		}
		catch (SearchException e) {
			assertThat( e ).hasMessageContaining( "Unknown field '" + Spatial.COORDINATES_DEFAULT_FIELD + "'" );
		}
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-1470")
	void testSpatialQueryOnWrongFieldThrowsException() throws Exception {
		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( POI.class ).get();

		try {
			builder.spatial()
					.onField( "foo" )
					.within( 1, Unit.KM )
					.ofLatitude( 0d )
					.andLongitude( 0d )
					.createQuery();
			fail( "Building an invalid spatial query should fail" );
		}
		catch (SearchException e) {
			assertThat( e ).hasMessageContaining( "Unknown field 'foo'" );
		}
	}

	@Test
	void testSpatialAnnotationOnFieldLevel() {
		//Point center = Point.fromDegrees( 24, 31.5 ); // 50.79 km fromBoundingCircle 24.32
		double centerLatitude = 24;
		double centerLongitude = 31.5;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( Event.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "location" )
				.within( 50, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, Event.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "location" )
				.within( 51, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, Event.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialAnnotationWithSubAnnotationsLevel() {
		//Point center = Point.fromDegrees( 24, 31.5 ); // 50.79 km fromBoundingCircle 24.32
		double centerLatitude = 24;
		double centerLongitude = 31.5;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( User.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "home" )
				.within( 50, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, User.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "home" )
				.within( 51, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, User.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialAnnotationWithSubAnnotationsLevelRangeMode() {
		//Point center = Point.fromDegrees( 24, 31.5 ); // 50.79 km fromBoundingCircle 24.32
		double centerLatitude = 24;
		double centerLongitude = 31.5;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( UserRange.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial()
				.within( 50, Unit.KM )
				.ofLatitude( centerLatitude )
				.andLongitude( centerLongitude )
				.createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, UserRange.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial()
				.within( 51, Unit.KM )
				.ofLatitude( centerLatitude )
				.andLongitude( centerLongitude )
				.createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, UserRange.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialsAnnotation() {
		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( UserEx.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial()
				.within( 100.0d, Unit.KM )
				.ofLatitude( 24.0d )
				.andLongitude( 31.5d )
				.createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, UserEx.class );
		List results = hibQuery.list();
		assertThat( results ).hasSize( 1 );

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "work" )
				.within( 100.0d, Unit.KM ).ofLatitude( 12.0d ).andLongitude( 27.5d ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, UserEx.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialAnnotationOnFieldLevelRangeMode() {
		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( RangeEvent.class ).get();

		double centerLatitude = 24;
		double centerLongitude = 31.5;

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "location" )
				.within( 50, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, RangeEvent.class );


		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "location" )
				.within( 51, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, RangeEvent.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialAnnotationOnClassLevel() throws Exception {
		//Point center = Point.fromDegrees( 24, 31.5 ); // 50.79 km fromBoundingCircle 24.32
		double centerLatitude = 24;
		double centerLongitude = 31.5;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( Hotel.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "hotel_location" )
				.within( 50, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, Hotel.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "hotel_location" )
				.within( 51, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, Hotel.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialAnnotationOnClassLevelRangeMode() {
		//Point center = Point.fromDegrees( 24, 31.5 ); // 50.79 km fromBoundingCircle 24.32
		double centerLatitude = 24;
		double centerLongitude = 31.5;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( RangeHotel.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder
				.spatial()
				.within( 50, Unit.KM )
				.ofLatitude( centerLatitude )
				.andLongitude( centerLongitude )
				.createQuery();


		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, RangeHotel.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder
				.spatial()
				.within( 51, Unit.KM )
				.ofLatitude( centerLatitude )
				.andLongitude( centerLongitude )
				.createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, RangeHotel.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );

		double endOfTheWorldLatitude = 0.0d;
		double endOfTheWorldLongitude = 180.0d;

		org.apache.lucene.search.Query luceneQuery3 = builder
				.spatial()
				.within( 112, Unit.KM )
				.ofLatitude( endOfTheWorldLatitude )
				.andLongitude( endOfTheWorldLongitude )
				.createQuery();

		org.hibernate.query.Query hibQuery3 = fullTextSession.createFullTextQuery( luceneQuery3, RangeHotel.class );
		List results3 = hibQuery3.list();
		assertThat( results3 ).hasSize( 2 );

		org.apache.lucene.search.Query luceneQuery4 = builder
				.spatial()
				.within( 100000, Unit.KM )
				.ofLatitude( endOfTheWorldLatitude )
				.andLongitude( endOfTheWorldLongitude )
				.createQuery();

		org.hibernate.query.Query hibQuery4 = fullTextSession.createFullTextQuery( luceneQuery4, RangeHotel.class );
		List results4 = hibQuery4.list();
		assertThat( results4 ).hasSize( 3 );
	}

	@Test
	void testSpatialAnnotationOnEmbeddableFieldLevel() {
		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( Restaurant.class ).get();

		double centerLatitude = 24;
		double centerLongitude = 31.5;

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "position.location" )
				.within( 50, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, Restaurant.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "position.location" )
				.within( 51, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, Restaurant.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void testSpatialLatLongOnGetters() {
		//Point center = Point.fromDegrees( 24, 31.5 ); // 50.79 km fromBoundingCircle 24.32
		double centerLatitude = 24;
		double centerLongitude = 31.5;

		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( GetterUser.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "home" )
				.within( 50, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, GetterUser.class );
		List results = hibQuery.list();
		assertThat( results ).isEmpty();

		org.apache.lucene.search.Query luceneQuery2 = builder.spatial().onField( "home" )
				.within( 51, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery2 = fullTextSession.createFullTextQuery( luceneQuery2, GetterUser.class );
		List results2 = hibQuery2.list();
		assertThat( results2 ).hasSize( 1 );
	}

	@Test
	void test180MeridianCross() {

		double centerLatitude = 37.769645d;
		double centerLongitude = -122.446428d;

		final QueryBuilder builder =
				fullTextSession.getSearchFactory().buildQueryBuilder().forEntity( DoubleIndexedPOI.class ).get();

		//Tests with FieldBridge
		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "location" )
				.within( 5000, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		FullTextQuery hibQuery = fullTextSession.createFullTextQuery( luceneQuery, DoubleIndexedPOI.class );
		hibQuery.setProjection( FullTextQuery.THIS, FullTextQuery.SPATIAL_DISTANCE );
		hibQuery.setSpatialParameters( centerLatitude, centerLongitude, "location" );
		hibQuery.setSort( builder.sort().byDistance().onField( "location" ).fromLatitude( centerLatitude )
				.andLongitude( centerLongitude ).createSort() );
		List results = hibQuery.list();
		assertEquals( 2, results.size() );
		Object[] firstResult = (Object[]) results.get( 0 );
		Object[] secondResult = (Object[]) results.get( 1 );
		assertEquals( 6.0492d, (Double) firstResult[1], 0.001 );
		assertEquals( 4132.8166d, (Double) secondResult[1], 1 );

		//Tests with @Longitude+@Latitude
		luceneQuery = builder.spatial()
				.within( 5000, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		hibQuery = fullTextSession.createFullTextQuery( luceneQuery, DoubleIndexedPOI.class );
		hibQuery.setProjection( FullTextQuery.THIS, FullTextQuery.SPATIAL_DISTANCE );
		hibQuery.setSpatialParameters( centerLatitude, centerLongitude, Spatial.COORDINATES_DEFAULT_FIELD );
		hibQuery.setSort( builder.sort().byDistance().onField( "location" ).fromLatitude( centerLatitude )
				.andLongitude( centerLongitude ).createSort() );
		results = hibQuery.list();
		assertEquals( 2, results.size() );
		firstResult = (Object[]) results.get( 0 );
		secondResult = (Object[]) results.get( 1 );
		assertEquals( 6.0492d, (Double) firstResult[1], 0.001 );
		assertEquals( 4132.8166d, (Double) secondResult[1], 1 );
	}

	@Override
	public Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
				POI.class,
				Event.class,
				Hotel.class,
				User.class,
				UserRange.class,
				UserEx.class,
				RangeHotel.class,
				RangeEvent.class,
				Restaurant.class,
				NonGeoPOI.class,
				GetterUser.class,
				MissingSpatialPOI.class,
				DoubleIndexedPOI.class
		};
	}

	private void assertNumberOfPointsOfInterestWithinRadius(double centerLatitude,
			double centerLongitude,
			double radius,
			int expectedPoiCount) {
		final QueryBuilder builder = fullTextSession.getSearchFactory()
				.buildQueryBuilder().forEntity( POI.class ).get();

		org.apache.lucene.search.Query luceneQuery = builder.spatial().onField( "location" )
				.within( radius, Unit.KM ).ofLatitude( centerLatitude ).andLongitude( centerLongitude ).createQuery();

		org.hibernate.query.Query hibQuery = fullTextSession.createFullTextQuery( luceneQuery, POI.class );
		List results = hibQuery.list();
		assertThat( results ).as( "Unexpected number of POIs within radius" ).hasSize( expectedPoiCount );
	}
}

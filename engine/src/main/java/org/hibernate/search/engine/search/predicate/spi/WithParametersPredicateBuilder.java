/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.search.predicate.spi;

import java.util.function.Function;

import org.hibernate.search.engine.search.common.NamedValues;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;

public interface WithParametersPredicateBuilder extends SearchPredicateBuilder {
	void creator(Function<? super NamedValues, ? extends PredicateFinalStep> predicateDefinition);
}
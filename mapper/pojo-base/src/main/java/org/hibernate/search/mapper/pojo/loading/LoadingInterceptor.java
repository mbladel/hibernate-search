/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.pojo.loading;

import org.hibernate.search.mapper.pojo.intercepting.LoadingInvocationContext;

/**
 * A mass indexing loading interceptor.
 *
 * @param <O> The options for loading process
 */
public interface LoadingInterceptor<O> {

	void intercept(LoadingInvocationContext<? extends O> ictx) throws Exception;

}
/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.spi.server;

import com.sitewhere.spi.server.lifecycle.LifecycleStatus;

/**
 * Contains runtime information about an {@link ISiteWhereTenantEngine}.
 * 
 * @author Derek
 */
public interface ISiteWhereTenantEngineState {

	/**
	 * Get lifecycle status of tenant engine.
	 * 
	 * @return
	 */
	public LifecycleStatus getLifecycleStatus();
}
/*
 * IDeviceSpecificationCreateRequest.java 
 * --------------------------------------------------------------------------------------
 * Copyright (c) Reveal Technologies, LLC. All rights reserved. http://www.reveal-tech.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.spi.device.request;

import com.sitewhere.spi.common.IMetadataProvider;

/**
 * Interface for arguments needed to create a device specification.
 * 
 * @author Derek
 */
public interface IDeviceSpecificationCreateRequest extends IMetadataProvider {

	/**
	 * Get name that describes specification.
	 * 
	 * @return
	 */
	public String getName();

	/**
	 * Get id for specification asset type.
	 * 
	 * @return
	 */
	public String getAssetId();

	/**
	 * Allows the specification id to be specified. (Optional)
	 * 
	 * @return
	 */
	public String getSpecificationId();
}
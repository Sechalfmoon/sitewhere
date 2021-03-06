/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.inbound;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.device.event.request.IDeviceStreamCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceStreamDataCreateRequest;
import com.sitewhere.spi.device.event.request.ISendDeviceStreamDataRequest;

/**
 * Handles requests related to device streams.
 * 
 * @author Derek
 */
public class DeviceStreamProcessor extends InboundEventProcessor {

    /** Static logger instance */
    private static Log LOGGER = LogFactory.getLog(DeviceStreamProcessor.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.spi.device.event.processor.IInboundEventProcessor#
     * onDeviceStreamCreateRequest(java.lang.String, java.lang.String,
     * com.sitewhere.spi.device.event.request.IDeviceStreamCreateRequest)
     */
    @Override
    public void onDeviceStreamCreateRequest(String hardwareId, String originator, IDeviceStreamCreateRequest request)
	    throws SiteWhereException {
	// getDeviceCommunication(getTenantEngine().getTenant()).getDeviceStreamManager()
	// .handleDeviceStreamRequest(hardwareId, request);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.spi.device.event.processor.IInboundEventProcessor#
     * onDeviceStreamDataCreateRequest(java.lang.String, java.lang.String,
     * java.lang.String,
     * com.sitewhere.spi.device.event.request.IDeviceStreamDataCreateRequest)
     */
    @Override
    public void onDeviceStreamDataCreateRequest(String hardwareId, String originator,
	    IDeviceStreamDataCreateRequest request) throws SiteWhereException {
	// getDeviceCommunication(getTenantEngine().getTenant()).getDeviceStreamManager()
	// .handleDeviceStreamDataRequest(hardwareId, request);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.device.event.processor.InboundEventProcessor#
     * onSendDeviceStreamDataRequest(java.lang.String, java.lang.String,
     * com.sitewhere.spi.device.event.request.ISendDeviceStreamDataRequest)
     */
    @Override
    public void onSendDeviceStreamDataRequest(String hardwareId, String originator,
	    ISendDeviceStreamDataRequest request) throws SiteWhereException {
	// getDeviceCommunication(getTenantEngine().getTenant()).getDeviceStreamManager()
	// .handleSendDeviceStreamDataRequest(hardwareId, request);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#getLogger()
     */
    @Override
    public Log getLogger() {
	return LOGGER;
    }
}
/*
 * HBaseDeviceSpecification.java 
 * --------------------------------------------------------------------------------------
 * Copyright (c) Reveal Technologies, LLC. All rights reserved. http://www.reveal-tech.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.hbase.device;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;

import com.sitewhere.core.SiteWherePersistence;
import com.sitewhere.hbase.ISiteWhereHBase;
import com.sitewhere.hbase.ISiteWhereHBaseClient;
import com.sitewhere.hbase.common.HBaseUtils;
import com.sitewhere.hbase.common.MarshalUtils;
import com.sitewhere.hbase.common.Pager;
import com.sitewhere.hbase.uid.IdManager;
import com.sitewhere.rest.model.device.DeviceSpecification;
import com.sitewhere.rest.model.search.SearchResults;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.SiteWhereSystemException;
import com.sitewhere.spi.device.IDeviceSpecification;
import com.sitewhere.spi.device.request.IDeviceSpecificationCreateRequest;
import com.sitewhere.spi.error.ErrorCode;
import com.sitewhere.spi.error.ErrorLevel;
import com.sitewhere.spi.search.ISearchCriteria;

/**
 * HBase specifics for dealing with SiteWhere device specifications.
 * 
 * @author Derek
 */
public class HBaseDeviceSpecification {

	/** Length of device identifier (subset of 8 byte long) */
	public static final int SPEC_IDENTIFIER_LENGTH = 4;

	/** Length of command identifier (subset of 8 byte long) */
	public static final int COMMAND_IDENTIFIER_LENGTH = 4;

	/** Column qualifier for command counter */
	public static final byte[] COMMAND_COUNTER = Bytes.toBytes("commandctr");

	/**
	 * Create a device specification.
	 * 
	 * @param hbase
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static IDeviceSpecification createDeviceSpecification(ISiteWhereHBaseClient hbase,
			IDeviceSpecificationCreateRequest request) throws SiteWhereException {
		String uuid = null;
		if (request.getSpecificationId() != null) {
			uuid = IdManager.getInstance().getSpecificationKeys().useExistingId(uuid);
		} else {
			uuid = IdManager.getInstance().getSpecificationKeys().createUniqueId();
		}

		// Use common logic so all backend implementations work the same.
		DeviceSpecification specification =
				SiteWherePersistence.deviceSpecificationCreateLogic(request, uuid);

		Long value = IdManager.getInstance().getSpecificationKeys().getValue(specification.getToken());
		if (value == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidDeviceSpecificationToken, ErrorLevel.ERROR);
		}
		byte[] primary = getPrimaryRowKey(value);
		byte[] json = MarshalUtils.marshalJson(specification);

		byte[] maxLong = Bytes.toBytes(Long.MAX_VALUE);
		HTableInterface devices = null;
		try {
			devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
			Put put = new Put(primary);
			put.add(ISiteWhereHBase.FAMILY_ID, ISiteWhereHBase.JSON_CONTENT, json);
			put.add(ISiteWhereHBase.FAMILY_ID, COMMAND_COUNTER, maxLong);
			devices.put(put);
		} catch (IOException e) {
			throw new SiteWhereException("Unable to put device specification data.", e);
		} finally {
			HBaseUtils.closeCleanly(devices);
		}

		return specification;
	}

	/**
	 * Get a device specification by unique token.
	 * 
	 * @param hbase
	 * @param token
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceSpecification getDeviceSpecificationByToken(ISiteWhereHBaseClient hbase, String token)
			throws SiteWhereException {
		Long specId = IdManager.getInstance().getSpecificationKeys().getValue(token);
		if (specId == null) {
			return null;
		}
		byte[] key = getPrimaryRowKey(specId);

		HTableInterface devices = null;
		try {
			devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
			Get get = new Get(key);
			get.addColumn(ISiteWhereHBase.FAMILY_ID, ISiteWhereHBase.JSON_CONTENT);
			Result result = devices.get(get);
			if (result.size() != 1) {
				throw new SiteWhereException("Expected one JSON entry for device specification and found: "
						+ result.size());
			}
			return MarshalUtils.unmarshalJson(result.value(), DeviceSpecification.class);
		} catch (IOException e) {
			throw new SiteWhereException("Unable to load device specification by token.", e);
		} finally {
			HBaseUtils.closeCleanly(devices);
		}
	}

	/**
	 * Update an existing device specification.
	 * 
	 * @param hbase
	 * @param token
	 * @param request
	 * @return
	 * @throws SiteWhereException
	 */
	public static IDeviceSpecification updateDeviceSpecification(ISiteWhereHBaseClient hbase, String token,
			IDeviceSpecificationCreateRequest request) throws SiteWhereException {
		DeviceSpecification updated = assertDeviceSpecification(hbase, token);
		SiteWherePersistence.deviceSpecificationUpdateLogic(request, updated);
		return putDeviceSpecificationJson(hbase, updated);
	}

	/**
	 * List device specifications that match the given search criteria.
	 * 
	 * @param hbase
	 * @param includeDeleted
	 * @param criteria
	 * @return
	 * @throws SiteWhereException
	 */
	public static SearchResults<IDeviceSpecification> listDeviceSpecifications(ISiteWhereHBaseClient hbase,
			boolean includeDeleted, ISearchCriteria criteria) throws SiteWhereException {
		Pager<byte[]> matches = getFilteredDeviceSpecifications(hbase, includeDeleted, criteria);
		List<IDeviceSpecification> response = new ArrayList<IDeviceSpecification>();
		for (byte[] json : matches.getResults()) {
			response.add(MarshalUtils.unmarshalJson(json, DeviceSpecification.class));
		}
		return new SearchResults<IDeviceSpecification>(response, matches.getTotal());
	}

	/**
	 * Get list of device specifications based on filter criteria.
	 * 
	 * @param hbase
	 * @param includeDeleted
	 * @param criteria
	 * @return
	 * @throws SiteWhereException
	 */
	protected static Pager<byte[]> getFilteredDeviceSpecifications(ISiteWhereHBaseClient hbase,
			boolean includeDeleted, ISearchCriteria criteria) throws SiteWhereException {
		HTableInterface devices = null;
		ResultScanner scanner = null;
		try {
			devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
			Scan scan = new Scan();
			scan.setStartRow(new byte[] { DeviceRecordType.DeviceSpecification.getType() });
			scan.setStopRow(new byte[] { DeviceRecordType.End.getType() });
			scanner = devices.getScanner(scan);

			Pager<byte[]> pager = new Pager<byte[]>(criteria);
			for (Result result : scanner) {
				byte[] row = result.getRow();

				// Only match specification rows.
				if ((row[0] != DeviceRecordType.DeviceSpecification.getType())
						|| (row[SPEC_IDENTIFIER_LENGTH + 1] != DeviceSpecificationRecordType.DeviceSpecification.getType())) {
					continue;
				}

				boolean shouldAdd = true;
				byte[] json = null;
				for (KeyValue column : result.raw()) {
					byte[] qualifier = column.getQualifier();
					if ((Bytes.equals(ISiteWhereHBase.DELETED, qualifier)) && (!includeDeleted)) {
						shouldAdd = false;
					}
					if (Bytes.equals(ISiteWhereHBase.JSON_CONTENT, qualifier)) {
						json = column.getValue();
					}
				}
				if ((shouldAdd) && (json != null)) {
					pager.process(json);
				}
			}
			return pager;
		} catch (IOException e) {
			throw new SiteWhereException("Error scanning device specification rows.", e);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
			HBaseUtils.closeCleanly(devices);
		}
	}

	/**
	 * Either deletes a device specification or marks it as deleted.
	 * 
	 * @param hbase
	 * @param token
	 * @param force
	 * @return
	 * @throws SiteWhereException
	 */
	public static IDeviceSpecification deleteDeviceSpecification(ISiteWhereHBaseClient hbase, String token,
			boolean force) throws SiteWhereException {
		DeviceSpecification existing = assertDeviceSpecification(hbase, token);
		existing.setDeleted(true);

		Long specId = IdManager.getInstance().getSpecificationKeys().getValue(token);
		byte[] key = getPrimaryRowKey(specId);
		if (force) {
			IdManager.getInstance().getSpecificationKeys().delete(token);
			HTableInterface devices = null;
			try {
				Delete delete = new Delete(key);
				devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
				devices.delete(delete);
			} catch (IOException e) {
				throw new SiteWhereException("Unable to delete device specification.", e);
			} finally {
				HBaseUtils.closeCleanly(devices);
			}
		} else {
			byte[] marker = { (byte) 0x01 };
			SiteWherePersistence.setUpdatedEntityMetadata(existing);
			byte[] updated = MarshalUtils.marshalJson(existing);

			HTableInterface devices = null;
			try {
				devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
				Put put = new Put(key);
				put.add(ISiteWhereHBase.FAMILY_ID, ISiteWhereHBase.JSON_CONTENT, updated);
				put.add(ISiteWhereHBase.FAMILY_ID, ISiteWhereHBase.DELETED, marker);
				devices.put(put);
			} catch (IOException e) {
				throw new SiteWhereException("Unable to set deleted flag for device specification.", e);
			} finally {
				HBaseUtils.closeCleanly(devices);
			}
		}
		return existing;
	}

	/**
	 * Get a device specification by token or throw an exception if not found.
	 * 
	 * @param hbase
	 * @param token
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceSpecification assertDeviceSpecification(ISiteWhereHBaseClient hbase, String token)
			throws SiteWhereException {
		DeviceSpecification existing = getDeviceSpecificationByToken(hbase, token);
		if (existing == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidDeviceSpecificationToken, ErrorLevel.ERROR);
		}
		return existing;
	}

	/**
	 * Save the JSON representation of a device specification.
	 * 
	 * @param hbase
	 * @param specification
	 * @return
	 * @throws SiteWhereException
	 */
	public static DeviceSpecification putDeviceSpecificationJson(ISiteWhereHBaseClient hbase,
			DeviceSpecification specification) throws SiteWhereException {
		Long value = IdManager.getInstance().getSpecificationKeys().getValue(specification.getToken());
		if (value == null) {
			throw new SiteWhereSystemException(ErrorCode.InvalidDeviceSpecificationToken, ErrorLevel.ERROR);
		}
		byte[] primary = getPrimaryRowKey(value);
		byte[] json = MarshalUtils.marshalJson(specification);

		HTableInterface devices = null;
		try {
			devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
			Put put = new Put(primary);
			put.add(ISiteWhereHBase.FAMILY_ID, ISiteWhereHBase.JSON_CONTENT, json);
			devices.put(put);
		} catch (IOException e) {
			throw new SiteWhereException("Unable to put device specification data.", e);
		} finally {
			HBaseUtils.closeCleanly(devices);
		}

		return specification;
	}

	/**
	 * Get the unique device identifier based on the long value associated with the device
	 * UUID. This will be a subset of the full 8-bit long value.
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] getTruncatedIdentifier(Long value) {
		byte[] bytes = Bytes.toBytes(value);
		byte[] result = new byte[SPEC_IDENTIFIER_LENGTH];
		System.arraycopy(bytes, bytes.length - SPEC_IDENTIFIER_LENGTH, result, 0, SPEC_IDENTIFIER_LENGTH);
		return result;
	}

	/**
	 * Allocate the next command id and return the new value. (Each id is less than the
	 * last)
	 * 
	 * @param hbase
	 * @param specId
	 * @return
	 * @throws SiteWhereException
	 */
	public static Long allocateNextCommandId(ISiteWhereHBaseClient hbase, Long specId)
			throws SiteWhereException {
		byte[] primary = getPrimaryRowKey(specId);
		HTableInterface devices = null;
		try {
			devices = hbase.getTableInterface(ISiteWhereHBase.DEVICES_TABLE_NAME);
			Increment increment = new Increment(primary);
			increment.addColumn(ISiteWhereHBase.FAMILY_ID, COMMAND_COUNTER, -1);
			Result result = devices.increment(increment);
			return Bytes.toLong(result.value());
		} catch (IOException e) {
			throw new SiteWhereException("Unable to allocate next command id.", e);
		} finally {
			HBaseUtils.closeCleanly(devices);
		}
	}

	/**
	 * Get row key for a device specification with the given id.
	 * 
	 * @param specificationId
	 * @return
	 */
	public static byte[] getPrimaryRowKey(Long specificationId) {
		ByteBuffer buffer = ByteBuffer.allocate(SPEC_IDENTIFIER_LENGTH + 2);
		buffer.put(DeviceRecordType.DeviceSpecification.getType());
		buffer.put(getTruncatedIdentifier(specificationId));
		buffer.put(DeviceSpecificationRecordType.DeviceSpecification.getType());
		return buffer.array();
	}

	/**
	 * Get the unique command identifier based on the long value associated with the
	 * command UUID. This will be a subset of the full 8-bit long value.
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] getCommandIdentifier(Long value) {
		byte[] bytes = Bytes.toBytes(value);
		byte[] result = new byte[COMMAND_IDENTIFIER_LENGTH];
		System.arraycopy(bytes, bytes.length - COMMAND_IDENTIFIER_LENGTH, result, 0,
				COMMAND_IDENTIFIER_LENGTH);
		return result;
	}

	/**
	 * Get prefix for a device command row for the given specification.
	 * 
	 * @param specificationId
	 * @return
	 */
	public static byte[] getDeviceCommandRowPrefix(Long specificationId) {
		ByteBuffer buffer = ByteBuffer.allocate(1 + SPEC_IDENTIFIER_LENGTH + 1);
		buffer.put(DeviceRecordType.DeviceSpecification.getType());
		buffer.put(getTruncatedIdentifier(specificationId));
		buffer.put(DeviceSpecificationRecordType.DeviceCommand.getType());
		return buffer.array();
	}

	/**
	 * Get prefix after the last defined specification record type.
	 * 
	 * @param specificationId
	 * @return
	 */
	public static byte[] getEndRowPrefix(Long specificationId) {
		ByteBuffer buffer = ByteBuffer.allocate(1 + SPEC_IDENTIFIER_LENGTH + 1);
		buffer.put(DeviceRecordType.DeviceSpecification.getType());
		buffer.put(getTruncatedIdentifier(specificationId));
		buffer.put(DeviceSpecificationRecordType.End.getType());
		return buffer.array();
	}

	/**
	 * Get row key for a device command based on spec and unique id.
	 * 
	 * @param specId
	 * @param value
	 * @return
	 */
	public static byte[] getDeviceCommandRowKey(Long specId, Long value) {
		byte[] prefix = getDeviceCommandRowPrefix(specId);
		byte[] uid = getCommandIdentifier(value);
		ByteBuffer buffer = ByteBuffer.allocate(prefix.length + uid.length);
		buffer.put(prefix);
		buffer.put(uid);
		return buffer.array();
	}
}
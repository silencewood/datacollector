/*
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.processor.lookup;

import com.google.common.cache.CacheLoader;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.lib.salesforce.DataType;
import com.streamsets.pipeline.lib.salesforce.Errors;
import com.streamsets.pipeline.lib.salesforce.ForceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class ForceLookupLoader extends CacheLoader<String, Map<String, Field>> {
  private static final Logger LOG = LoggerFactory.getLogger(ForceLookupLoader.class);

  private final Map<String, String> columnsToFields;
  private final Map<String, String> columnsToDefaults;
  private final Map<String, DataType> columnsToTypes;
  private final PartnerConnection partnerConnection;
  private final boolean createSalesforceNsHeaders;
  private final String salesforceNsHeaderPrefix;

  ForceLookupLoader(
      PartnerConnection partnerConnection,
      Map<String, String> columnsToFields,
      Map<String, String> columnsToDefaults,
      Map<String, DataType> columnsToTypes,
      boolean createSalesforceNsHeaders,
      String salesforceNsHeaderPrefix
  ) {
    this.partnerConnection = partnerConnection;
    this.columnsToFields = columnsToFields;
    this.columnsToDefaults = columnsToDefaults;
    this.columnsToTypes = columnsToTypes;
    this.createSalesforceNsHeaders = createSalesforceNsHeaders;
    this.salesforceNsHeaderPrefix = salesforceNsHeaderPrefix;
  }

  @Override
  public Map<String, Field> load(String key) throws Exception {
    return lookupValuesForRecord(key);
  }

  private Map<String, Field> lookupValuesForRecord(String preparedQuery) throws StageException {
    Map<String, Field> fieldMap = new HashMap<>();

    try {
      Map<String, Map<String, com.sforce.soap.partner.Field>> metadataMap =
          ForceUtils.getMetadataMap(partnerConnection, ForceUtils.getSobjectTypeFromQuery(preparedQuery));

      QueryResult queryResult = partnerConnection.query(preparedQuery);

      SObject[] records = queryResult.getRecords();

      LOG.info("Retrieved {} records", records.length);

      if (records.length > 0) {
        // TODO - handle multiple records (SDC-4739)

        fieldMap = ForceUtils.addFields(
            records[0],
            metadataMap,
            createSalesforceNsHeaders, salesforceNsHeaderPrefix, columnsToTypes);
      } else {
        // Salesforce returns no row. Use default values.
        for (String key : columnsToFields.keySet()) {
          String val = columnsToDefaults.get(key);
          try {
            if (columnsToTypes.get(key) != DataType.USE_SALESFORCE_TYPE) {
              Field field = Field.create(Field.Type.valueOf(columnsToTypes.get(key).getLabel()), val);
              fieldMap.put(key, field);
            }
          } catch (IllegalArgumentException e) {
            throw new OnRecordErrorException(Errors.FORCE_20, key, val, e);
          }
        }
      }
    } catch (ConnectionException e) {
      LOG.error(Errors.FORCE_17.getMessage(), preparedQuery, e);
      throw new OnRecordErrorException(Errors.FORCE_17, preparedQuery, e.getMessage());
    }

    return fieldMap;
  }
}

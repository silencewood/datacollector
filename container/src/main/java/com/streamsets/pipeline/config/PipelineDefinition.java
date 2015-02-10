/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.config;

import com.google.common.collect.ImmutableMap;
import com.streamsets.pipeline.api.ChooserMode;
import com.streamsets.pipeline.api.ChooserValues;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.base.BaseEnumChooserValues;
import com.streamsets.pipeline.api.impl.LocalizableMessage;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.stagelibrary.StageLibraryTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class PipelineDefinition {
  private static final String PIPELINE_RESOURCE_BUNDLE = "PipelineDefinition-bundle";

  private final static String DELIVERY_GUARANTEE_LABEL_KEY = "config.deliveryGuarantee.label";
  private final static String DELIVERY_GUARANTEE_LABEL_VALUE = "Delivery Guarantee";

  private final static String DELIVERY_GUARANTEE_DESCRIPTION_KEY = "config.deliveryGuarantee.description";
  private final static String DELIVERY_GUARANTEE_DESCRIPTION_DEFAULT = "This is the option for the delivery guarantee";

  private final static String DELIVERY_GUARANTEE_AT_LEAST_ONCE_KEY = "config.deliveryGuarantee.AT_LEAST_ONCE";
  private final static String DELIVERY_GUARANTEE_AT_LEAST_ONCE_DEFAULT = "At Least Once";
  private final static String DELIVERY_GUARANTEE_AT_MOST_ONCE_KEY = "config.deliveryGuarantee.AT_MOST_ONCE";
  private final static String DELIVERY_GUARANTEE_AT_MOST_ONCE_DEFAULT = "At Most Once";

  private StageLibraryTask stageLibrary;
  /*The config definitions of the pipeline*/
  private List<ConfigDefinition> configDefinitions;

  public PipelineDefinition(StageLibraryTask stageLibrary) {
    this.stageLibrary = stageLibrary;
    configDefinitions = new ArrayList<>();
    configDefinitions.add(createDeliveryGuaranteeOption());
    configDefinitions.addAll(createBadRecordsHandlingConfigs());
  }

  /*Need this API for Jackson to serialize*/
  public List<ConfigDefinition> getConfigDefinitions() {
    return configDefinitions;
  }

  public ConfigGroupDefinition getConfigGroupDefinition() {
    List<Map<String, String>> groups = new ArrayList<>();
    groups.add(ImmutableMap.of("name", "BAD_RECORDS", "label", "Bad Records"));
    return new ConfigGroupDefinition(null, groups);
  }

  @Override
  public String toString() {
    return Utils.format("PipelineDefinition[configDefinitions='{}']", configDefinitions);
  }

  /**************************************************************/
  /********************** Private methods ***********************/
  /**************************************************************/

  private ConfigDefinition createDeliveryGuaranteeOption() {

    List<String> gdLabels = new ArrayList<>(2);
    gdLabels.add(new LocalizableMessage(getClass().getClassLoader(), PIPELINE_RESOURCE_BUNDLE,
        DELIVERY_GUARANTEE_AT_LEAST_ONCE_KEY, DELIVERY_GUARANTEE_AT_LEAST_ONCE_DEFAULT, null).getLocalized());
    gdLabels.add(new LocalizableMessage(getClass().getClassLoader(), PIPELINE_RESOURCE_BUNDLE,
        DELIVERY_GUARANTEE_AT_MOST_ONCE_KEY, DELIVERY_GUARANTEE_AT_MOST_ONCE_DEFAULT, null).getLocalized());

    List<String> gdValues = new ArrayList<>(2);
    gdValues.add(DeliveryGuarantee.AT_LEAST_ONCE.name());
    gdValues.add(DeliveryGuarantee.AT_MOST_ONCE.name());

    ModelDefinition gdModelDefinition = new ModelDefinition(ModelType.VALUE_CHOOSER,
      ChooserMode.PROVIDED, "",  gdValues, gdLabels, null);

    //Localize label and description for "delivery guarantee" config option
    String dgLabel = new LocalizableMessage(getClass().getClassLoader(), PIPELINE_RESOURCE_BUNDLE,
        DELIVERY_GUARANTEE_LABEL_KEY, DELIVERY_GUARANTEE_LABEL_VALUE, null).getLocalized();
    String dgDescription = new LocalizableMessage(getClass().getClassLoader(), PIPELINE_RESOURCE_BUNDLE,
        DELIVERY_GUARANTEE_DESCRIPTION_KEY, DELIVERY_GUARANTEE_DESCRIPTION_DEFAULT, null).getLocalized();

    ConfigDefinition dgConfigDef = new ConfigDefinition(
      "deliveryGuarantee",
      ConfigDef.Type.MODEL,
      dgLabel,
      dgDescription,
      DeliveryGuarantee.AT_LEAST_ONCE.name(),
      true,
      "",
      "deliveryGuarantee",
      gdModelDefinition,
      "",
      new String[] {},
      0);

    return dgConfigDef;
  }

  private List<String> getErrorHandlingOptions(boolean value) {
    List<String> list = new ArrayList<>();
    for (StageDefinition def : stageLibrary.getStages()) {
      if (def.getType() == StageType.TARGET && def.isErrorStage()) {
        if (value) {
          list.add(def.getLibrary() + "::" + def.getName() + "::" + def.getVersion());
        } else {
          list.add(def.getLabel() + " - " + def.getLibraryLabel());
        }
      }
    }
    return list;
  }

  private List<String> getErrorHandlingValues() {
    return getErrorHandlingOptions(true);
  }

  private List<String> getErrorHandlingLabels() {
    return getErrorHandlingOptions(false);
  }

  private List<ConfigDefinition> createBadRecordsHandlingConfigs() {
    List<ConfigDefinition> configs = new ArrayList<>();
    ModelDefinition model = new ModelDefinition(ModelType.VALUE_CHOOSER, ChooserMode.PROVIDED, "",
                                                getErrorHandlingValues(), getErrorHandlingLabels(), null);
    ConfigDefinition config = new ConfigDefinition(
        "badRecordsHandling",
        ConfigDef.Type.MODEL,
        "Bad Records Handling",
        "",
        "",
        true,
        "BAD_RECORDS",
        "",
        model,
        "",
        new String[] {},
        10);
    configs.add(config);

    return configs;
  }

}

/*
 * Copyright 2024 Datastrato Pvt Ltd.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.gravitino.client;

import static com.datastrato.gravitino.dto.util.DTOConverters.toDTO;

import com.datastrato.gravitino.Audit;
import com.datastrato.gravitino.Namespace;
import com.datastrato.gravitino.dto.rel.TableDTO;
import com.datastrato.gravitino.dto.rel.partitions.PartitionDTO;
import com.datastrato.gravitino.dto.requests.AddPartitionsRequest;
import com.datastrato.gravitino.dto.responses.PartitionListResponse;
import com.datastrato.gravitino.dto.responses.PartitionNameListResponse;
import com.datastrato.gravitino.dto.responses.PartitionResponse;
import com.datastrato.gravitino.exceptions.NoSuchPartitionException;
import com.datastrato.gravitino.exceptions.PartitionAlreadyExistsException;
import com.datastrato.gravitino.rel.Column;
import com.datastrato.gravitino.rel.SupportsPartitions;
import com.datastrato.gravitino.rel.Table;
import com.datastrato.gravitino.rel.expressions.distributions.Distribution;
import com.datastrato.gravitino.rel.expressions.sorts.SortOrder;
import com.datastrato.gravitino.rel.expressions.transforms.Transform;
import com.datastrato.gravitino.rel.indexes.Index;
import com.datastrato.gravitino.rel.partitions.Partition;
import com.google.common.annotations.VisibleForTesting;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.SneakyThrows;

/** Represents a relational table. */
public class RelationalTable implements Table, SupportsPartitions {

  /**
   * Creates a new RelationalTable.
   *
   * @param namespace The namespace of the table.
   * @param tableDTO The table data transfer object.
   * @param restClient The REST client.
   * @return A new RelationalTable.
   */
  public static RelationalTable from(
      Namespace namespace, TableDTO tableDTO, RESTClient restClient) {
    return new RelationalTable(namespace, tableDTO, restClient);
  }

  private final TableDTO tableDTO;
  private final RESTClient restClient;
  private final Namespace namespace;

  /**
   * Creates a new RelationalTable.
   *
   * @param namespace The namespace of the table.
   * @param tableDTO The table data transfer object.
   * @param restClient The REST client.
   */
  public RelationalTable(Namespace namespace, TableDTO tableDTO, RESTClient restClient) {
    this.namespace = namespace;
    this.tableDTO = tableDTO;
    this.restClient = restClient;
  }

  /**
   * Returns the namespace of the table.
   *
   * @return The namespace of the table.
   */
  public Namespace namespace() {
    return namespace;
  }

  /**
   * Returns the name of the table.
   *
   * @return The name of the table.
   */
  @Override
  public String name() {
    return tableDTO.name();
  }

  /** @return the columns of the table. */
  @Override
  public Column[] columns() {
    return tableDTO.columns();
  }

  /** @return the partitioning of the table. */
  @Override
  public Transform[] partitioning() {
    return tableDTO.partitioning();
  }

  /** @return the sort order of the table. */
  @Override
  public SortOrder[] sortOrder() {
    return tableDTO.sortOrder();
  }

  /** @return the distribution of the table. */
  @Override
  public Distribution distribution() {
    return tableDTO.distribution();
  }

  /** @return the comment of the table. */
  @Nullable
  @Override
  public String comment() {
    return tableDTO.comment();
  }

  /** @return the properties of the table. */
  @Override
  public Map<String, String> properties() {
    return tableDTO.properties();
  }

  /** @return the audit information of the table. */
  @Override
  public Audit auditInfo() {
    return tableDTO.auditInfo();
  }

  /** @return the indexes of the table. */
  @Override
  public Index[] index() {
    return tableDTO.index();
  }

  /** @return The partition names of the table. */
  @Override
  public String[] listPartitionNames() {
    PartitionNameListResponse resp =
        restClient.get(
            getPartitionRequestPath(),
            PartitionNameListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    return resp.partitionNames();
  }

  /** @return The partition request path. */
  @VisibleForTesting
  public String getPartitionRequestPath() {
    return "api/metalakes/"
        + namespace.level(0)
        + "/catalogs/"
        + namespace.level(1)
        + "/schemas/"
        + namespace.level(2)
        + "/tables/"
        + name()
        + "/partitions";
  }

  /** @return The partitions of the table. */
  @Override
  public Partition[] listPartitions() {
    Map<String, String> params = new HashMap<>();
    params.put("details", "true");
    PartitionListResponse resp =
        restClient.get(
            getPartitionRequestPath(),
            params,
            PartitionListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    return resp.getPartitions();
  }

  /**
   * Returns the partition with the given name.
   *
   * @param partitionName the name of the partition
   * @return the partition with the given name
   * @throws NoSuchPartitionException if the partition does not exist, throws this exception.
   */
  @Override
  public Partition getPartition(String partitionName) throws NoSuchPartitionException {
    PartitionResponse resp =
        restClient.get(
            formatPartitionRequestPath(getPartitionRequestPath(), partitionName),
            PartitionResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    return resp.getPartition();
  }

  /**
   * Adds a partition to the table.
   *
   * @param partition The partition to add.
   * @return The added partition.
   * @throws PartitionAlreadyExistsException If the partition already exists, throws this exception.
   */
  @Override
  public Partition addPartition(Partition partition) throws PartitionAlreadyExistsException {
    AddPartitionsRequest req = new AddPartitionsRequest(new PartitionDTO[] {toDTO(partition)});
    req.validate();

    PartitionListResponse resp =
        restClient.post(
            getPartitionRequestPath(),
            req,
            PartitionListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    resp.validate();

    return resp.getPartitions()[0];
  }

  /**
   * Drops the partition with the given name.
   *
   * @param partitionName The identifier of the partition.
   * @return true if the partition is dropped, false otherwise.
   */
  @Override
  public boolean dropPartition(String partitionName) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the partitioning strategy of the table.
   *
   * @return the partitioning strategy of the table.
   */
  @Override
  public SupportsPartitions supportPartitions() throws UnsupportedOperationException {
    return this;
  }

  /**
   * Formats the partition request path.
   *
   * @param prefix The prefix of the path.
   * @param partitionName The name of the partition.
   * @return The formatted partition request path.
   */
  @VisibleForTesting
  @SneakyThrows // Encode charset is fixed to UTF-8, so this is safe.
  protected static String formatPartitionRequestPath(String prefix, String partitionName) {
    return prefix + "/" + URLEncoder.encode(partitionName, "UTF-8");
  }
}

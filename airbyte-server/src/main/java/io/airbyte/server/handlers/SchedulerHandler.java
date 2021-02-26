/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.server.handlers;

import com.google.common.annotations.VisibleForTesting;
import io.airbyte.api.model.CheckConnectionRead;
import io.airbyte.api.model.ConnectionIdRequestBody;
import io.airbyte.api.model.DestinationCoreConfig;
import io.airbyte.api.model.DestinationDefinitionIdRequestBody;
import io.airbyte.api.model.DestinationDefinitionSpecificationRead;
import io.airbyte.api.model.DestinationIdRequestBody;
import io.airbyte.api.model.DestinationUpdate;
import io.airbyte.api.model.JobInfoRead;
import io.airbyte.api.model.SourceCoreConfig;
import io.airbyte.api.model.SourceDefinitionIdRequestBody;
import io.airbyte.api.model.SourceDefinitionSpecificationRead;
import io.airbyte.api.model.SourceDiscoverSchemaRead;
import io.airbyte.api.model.SourceIdRequestBody;
import io.airbyte.api.model.SourceUpdate;
import io.airbyte.commons.docker.DockerUtils;
import io.airbyte.commons.enums.Enums;
import io.airbyte.commons.functional.CheckedSupplier;
import io.airbyte.commons.tuple.Pair;
import io.airbyte.config.DestinationConnection;
import io.airbyte.config.SourceConnection;
import io.airbyte.config.StandardCheckConnectionOutput;
import io.airbyte.config.StandardCheckConnectionOutput.Status;
import io.airbyte.config.StandardDestinationDefinition;
import io.airbyte.config.StandardDiscoverCatalogOutput;
import io.airbyte.config.StandardSourceDefinition;
import io.airbyte.config.StandardSync;
import io.airbyte.config.persistence.ConfigNotFoundException;
import io.airbyte.config.persistence.ConfigRepository;
import io.airbyte.protocol.models.ConnectorSpecification;
import io.airbyte.scheduler.Job;
import io.airbyte.scheduler.client.DefaultSchedulerSynchronousJobClient;
import io.airbyte.scheduler.client.SchedulerJobClient;
import io.airbyte.scheduler.client.SchedulerSynchronousJobClient;
import io.airbyte.scheduler.client.SynchronousJobException;
import io.airbyte.server.converters.CatalogConverter;
import io.airbyte.server.converters.ConfigurationUpdate;
import io.airbyte.server.converters.JobConverter;
import io.airbyte.server.converters.JobMetadataConverter;
import io.airbyte.server.converters.SpecFetcher;
import io.airbyte.validation.json.JsonSchemaValidator;
import io.airbyte.validation.json.JsonValidationException;
import java.io.IOException;
import java.util.UUID;

public class SchedulerHandler {

  private final ConfigRepository configRepository;
  private final SchedulerJobClient schedulerJobClient;
  private final SpecFetcher specFetcher;
  private final SchedulerSynchronousJobClient schedulerSynchronousJobClient;
  private final ConfigurationUpdate configurationUpdate;
  private final JsonSchemaValidator jsonSchemaValidator;

  public SchedulerHandler(ConfigRepository configRepository, SchedulerJobClient schedulerJobClient) {
    this(
        configRepository,
        schedulerJobClient,
        new ConfigurationUpdate(configRepository, new SpecFetcher(new DefaultSchedulerSynchronousJobClient(schedulerJobClient))),
        new JsonSchemaValidator(),
        new SpecFetcher(new DefaultSchedulerSynchronousJobClient(schedulerJobClient)),
        new DefaultSchedulerSynchronousJobClient(schedulerJobClient));
  }

  @VisibleForTesting
  SchedulerHandler(ConfigRepository configRepository,
                   SchedulerJobClient schedulerJobClient,
                   ConfigurationUpdate configurationUpdate,
                   JsonSchemaValidator jsonSchemaValidator,
                   SpecFetcher specFetcher,
                   SchedulerSynchronousJobClient schedulerSynchronousJobClient) {
    this.configRepository = configRepository;
    this.schedulerJobClient = schedulerJobClient;
    this.configurationUpdate = configurationUpdate;
    this.jsonSchemaValidator = jsonSchemaValidator;
    this.specFetcher = specFetcher;
    this.schedulerSynchronousJobClient = schedulerSynchronousJobClient;
  }

  public CheckConnectionRead checkSourceConnectionFromSourceId(SourceIdRequestBody sourceIdRequestBody)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final SourceConnection source = configRepository.getSourceConnection(sourceIdRequestBody.getSourceId());
    final StandardSourceDefinition sourceDef = configRepository.getStandardSourceDefinition(source.getSourceDefinitionId());
    final String imageName = DockerUtils.getTaggedImageName(sourceDef.getDockerRepository(), sourceDef.getDockerImageTag());

    final Pair<StandardCheckConnectionOutput, JobInfoRead> output =
        executeSynchronousJob(() -> schedulerSynchronousJobClient.createSourceCheckConnectionJob(source, imageName));
    return reportConnectionStatus(output);
  }

  public CheckConnectionRead checkSourceConnectionFromSourceCreate(SourceCoreConfig sourceConfig)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final StandardSourceDefinition sourceDef = configRepository.getStandardSourceDefinition(sourceConfig.getSourceDefinitionId());
    final String imageName = DockerUtils.getTaggedImageName(sourceDef.getDockerRepository(), sourceDef.getDockerImageTag());
    // todo (cgardens) - narrow the struct passed to the client. we are not setting fields that are
    // technically declared as required.
    final SourceConnection source = new SourceConnection()
        .withSourceDefinitionId(sourceConfig.getSourceDefinitionId())
        .withConfiguration(sourceConfig.getConnectionConfiguration());

    final Pair<StandardCheckConnectionOutput, JobInfoRead> output =
        executeSynchronousJob(() -> schedulerSynchronousJobClient.createSourceCheckConnectionJob(source, imageName));
    return reportConnectionStatus(output);
  }

  public CheckConnectionRead checkSourceConnectionFromSourceIdForUpdate(SourceUpdate sourceUpdate)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final SourceConnection updatedSource = configurationUpdate.source(sourceUpdate.getSourceId(), sourceUpdate.getConnectionConfiguration());

    final ConnectorSpecification spec = getSpecFromSourceDefinitionId(updatedSource.getSourceDefinitionId());
    jsonSchemaValidator.validate(spec.getConnectionSpecification(), updatedSource.getConfiguration());

    final SourceCoreConfig sourceCoreConfig = new SourceCoreConfig()
        .connectionConfiguration(updatedSource.getConfiguration())
        .sourceDefinitionId(updatedSource.getSourceDefinitionId());

    return checkSourceConnectionFromSourceCreate(sourceCoreConfig);
  }

  public CheckConnectionRead checkDestinationConnectionFromDestinationId(DestinationIdRequestBody destinationIdRequestBody)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final DestinationConnection destination = configRepository.getDestinationConnection(destinationIdRequestBody.getDestinationId());
    final StandardDestinationDefinition destinationDef = configRepository.getStandardDestinationDefinition(destination.getDestinationDefinitionId());
    final String imageName = DockerUtils.getTaggedImageName(destinationDef.getDockerRepository(), destinationDef.getDockerImageTag());
    final Pair<StandardCheckConnectionOutput, JobInfoRead> output =
        executeSynchronousJob(() -> schedulerSynchronousJobClient.createDestinationCheckConnectionJob(destination, imageName));
    return reportConnectionStatus(output);
  }

  public CheckConnectionRead checkDestinationConnectionFromDestinationCreate(DestinationCoreConfig destinationConfig)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final StandardDestinationDefinition destDef = configRepository.getStandardDestinationDefinition(destinationConfig.getDestinationDefinitionId());
    final String imageName = DockerUtils.getTaggedImageName(destDef.getDockerRepository(), destDef.getDockerImageTag());
    // todo (cgardens) - narrow the struct passed to the client. we are not setting fields that are
    // technically declared as required.
    final DestinationConnection destination = new DestinationConnection()
        .withDestinationDefinitionId(destinationConfig.getDestinationDefinitionId())
        .withConfiguration(destinationConfig.getConnectionConfiguration());
    final Pair<StandardCheckConnectionOutput, JobInfoRead> output =
        executeSynchronousJob(() -> schedulerSynchronousJobClient.createDestinationCheckConnectionJob(destination, imageName));
    return reportConnectionStatus(output);
  }

  public CheckConnectionRead checkDestinationConnectionFromDestinationIdForUpdate(DestinationUpdate destinationUpdate)
      throws JsonValidationException, IOException, ConfigNotFoundException {
    final DestinationConnection updatedDestination = configurationUpdate
        .destination(destinationUpdate.getDestinationId(), destinationUpdate.getConnectionConfiguration());

    final ConnectorSpecification spec = getSpecFromDestinationDefinitionId(updatedDestination.getDestinationDefinitionId());
    jsonSchemaValidator.validate(spec.getConnectionSpecification(), updatedDestination.getConfiguration());

    final DestinationCoreConfig destinationCoreConfig = new DestinationCoreConfig()
        .connectionConfiguration(updatedDestination.getConfiguration())
        .destinationDefinitionId(updatedDestination.getDestinationDefinitionId());

    return checkDestinationConnectionFromDestinationCreate(destinationCoreConfig);
  }

  public SourceDiscoverSchemaRead discoverSchemaForSourceFromSourceId(SourceIdRequestBody sourceIdRequestBody)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final SourceConnection source = configRepository.getSourceConnection(sourceIdRequestBody.getSourceId());
    final StandardSourceDefinition sourceDef = configRepository.getStandardSourceDefinition(source.getSourceDefinitionId());
    final String imageName = DockerUtils.getTaggedImageName(sourceDef.getDockerRepository(), sourceDef.getDockerImageTag());
    final Pair<StandardDiscoverCatalogOutput, JobInfoRead> output =
        executeSynchronousJob(() -> schedulerSynchronousJobClient.createDiscoverSchemaJob(source, imageName));
    return discoverJobToOutput(output);
  }

  public SourceDiscoverSchemaRead discoverSchemaForSourceFromSourceCreate(SourceCoreConfig sourceCreate)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final StandardSourceDefinition sourceDef = configRepository.getStandardSourceDefinition(sourceCreate.getSourceDefinitionId());
    final String imageName = DockerUtils.getTaggedImageName(sourceDef.getDockerRepository(), sourceDef.getDockerImageTag());
    // todo (cgardens) - narrow the struct passed to the client. we are not setting fields that are
    // technically declared as required.
    final SourceConnection source = new SourceConnection()
        .withSourceDefinitionId(sourceCreate.getSourceDefinitionId())
        .withConfiguration(sourceCreate.getConnectionConfiguration());
    final Pair<StandardDiscoverCatalogOutput, JobInfoRead> output =
        executeSynchronousJob(() -> schedulerSynchronousJobClient.createDiscoverSchemaJob(source, imageName));
    return discoverJobToOutput(output);
  }

  private static SourceDiscoverSchemaRead discoverJobToOutput(Pair<StandardDiscoverCatalogOutput, JobInfoRead> pair) {
    final SourceDiscoverSchemaRead sourceDiscoverSchemaRead = new SourceDiscoverSchemaRead();
    pair.getRightOptional().ifPresent(sourceDiscoverSchemaRead::jobInfo);

    pair.getLeftOptional()
        .map(StandardDiscoverCatalogOutput::getCatalog)
        .ifPresent(catalog -> sourceDiscoverSchemaRead.catalog(CatalogConverter.toApi(catalog)));

    return sourceDiscoverSchemaRead;
  }

  public SourceDefinitionSpecificationRead getSourceDefinitionSpecification(SourceDefinitionIdRequestBody sourceDefinitionIdRequestBody)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final UUID sourceDefinitionId = sourceDefinitionIdRequestBody.getSourceDefinitionId();
    final StandardSourceDefinition source = configRepository.getStandardSourceDefinition(sourceDefinitionId);
    final String imageName = DockerUtils.getTaggedImageName(source.getDockerRepository(), source.getDockerImageTag());
    final ConnectorSpecification spec = getConnectorSpecification(imageName);
    return new SourceDefinitionSpecificationRead()
        .connectionSpecification(spec.getConnectionSpecification())
        .documentationUrl(spec.getDocumentationUrl().toString())
        .sourceDefinitionId(sourceDefinitionId);
  }

  public DestinationDefinitionSpecificationRead getDestinationSpecification(DestinationDefinitionIdRequestBody destinationDefinitionIdRequestBody)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final UUID destinationDefinitionId = destinationDefinitionIdRequestBody.getDestinationDefinitionId();
    final StandardDestinationDefinition destination = configRepository.getStandardDestinationDefinition(destinationDefinitionId);
    final String imageName = DockerUtils.getTaggedImageName(destination.getDockerRepository(), destination.getDockerImageTag());
    final ConnectorSpecification spec = getConnectorSpecification(imageName);
    return new DestinationDefinitionSpecificationRead()
        .connectionSpecification(spec.getConnectionSpecification())
        .documentationUrl(spec.getDocumentationUrl().toString())
        .destinationDefinitionId(destinationDefinitionId);
  }

  public ConnectorSpecification getConnectorSpecification(String dockerImage) throws IOException {
    return specFetcher.execute(dockerImage);
  }

  public JobInfoRead syncConnection(final ConnectionIdRequestBody connectionIdRequestBody)
      throws ConfigNotFoundException, IOException, JsonValidationException {
    final UUID connectionId = connectionIdRequestBody.getConnectionId();
    final StandardSync standardSync = configRepository.getStandardSync(connectionId);

    final SourceConnection source = configRepository.getSourceConnection(standardSync.getSourceId());
    final DestinationConnection destination = configRepository.getDestinationConnection(standardSync.getDestinationId());

    final StandardSourceDefinition sourceDef = configRepository.getStandardSourceDefinition(source.getSourceDefinitionId());
    final String sourceImageName = DockerUtils.getTaggedImageName(sourceDef.getDockerRepository(), sourceDef.getDockerImageTag());

    final StandardDestinationDefinition destinationDef = configRepository.getStandardDestinationDefinition(destination.getDestinationDefinitionId());
    final String destinationImageName = DockerUtils.getTaggedImageName(destinationDef.getDockerRepository(), destinationDef.getDockerImageTag());

    final Job job = schedulerJobClient.createOrGetActiveSyncJob(
        source,
        destination,
        standardSync,
        sourceImageName,
        destinationImageName);

    return JobConverter.getJobInfoRead(job);
  }

  public JobInfoRead resetConnection(final ConnectionIdRequestBody connectionIdRequestBody)
      throws IOException, JsonValidationException, ConfigNotFoundException {
    final UUID connectionId = connectionIdRequestBody.getConnectionId();
    final StandardSync standardSync = configRepository.getStandardSync(connectionId);

    final DestinationConnection destination = configRepository.getDestinationConnection(standardSync.getDestinationId());

    final StandardDestinationDefinition destinationDef = configRepository.getStandardDestinationDefinition(destination.getDestinationDefinitionId());
    final String destinationImageName = DockerUtils.getTaggedImageName(destinationDef.getDockerRepository(), destinationDef.getDockerImageTag());

    final Job job = schedulerJobClient.createOrGetActiveResetConnectionJob(destination, standardSync, destinationImageName);

    return JobConverter.getJobInfoRead(job);
  }

  private CheckConnectionRead reportConnectionStatus(final Pair<StandardCheckConnectionOutput, JobInfoRead> pair) {
    final StandardCheckConnectionOutput checkConnectionOutput = pair.getLeftOptional()
        // the job should always produce an output, but if it does not, we assume a failure.
        .orElse(new StandardCheckConnectionOutput().withStatus(Status.FAILED));

    return new CheckConnectionRead()
        .status(Enums.convertTo(checkConnectionOutput.getStatus(), CheckConnectionRead.StatusEnum.class))
        .message(checkConnectionOutput.getMessage())
        .jobInfo(pair.getRightOptional().orElse(null));
  }

  private ConnectorSpecification getSpecFromSourceDefinitionId(UUID sourceDefId)
      throws IOException, JsonValidationException, ConfigNotFoundException {
    final StandardSourceDefinition sourceDef = configRepository.getStandardSourceDefinition(sourceDefId);
    final String imageName = DockerUtils.getTaggedImageName(sourceDef.getDockerRepository(), sourceDef.getDockerImageTag());
    return specFetcher.execute(imageName);
  }

  private ConnectorSpecification getSpecFromDestinationDefinitionId(UUID destDefId)
      throws IOException, JsonValidationException, ConfigNotFoundException {
    final StandardDestinationDefinition destinationDef = configRepository.getStandardDestinationDefinition(destDefId);
    final String imageName = DockerUtils.getTaggedImageName(destinationDef.getDockerRepository(), destinationDef.getDockerImageTag());
    return specFetcher.execute(imageName);
  }

  private static JobInfoRead getJobInfoFromException(SynchronousJobException synchronousJobException) {
    return JobMetadataConverter.getJobInfoRead(synchronousJobException.getJobMetadata());
  }

  public static <T> Pair<T, JobInfoRead> executeSynchronousJob(CheckedSupplier<T, Exception> supplier) throws IOException {
    try {
      return Pair.of(supplier.get(), null);
    } catch (SynchronousJobException e) {
      return Pair.of(null, getJobInfoFromException(e));
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}

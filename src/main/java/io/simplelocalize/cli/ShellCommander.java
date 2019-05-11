package io.simplelocalize.cli;

import io.simplelocalize.cli.client.SimpleLocalizeClient;
import io.simplelocalize.cli.configuration.Configuration;
import io.simplelocalize.cli.configuration.ConfigurationLoader;
import io.simplelocalize.cli.configuration.ConfigurationValidator;
import io.simplelocalize.cli.processor.ProcessResult;
import io.simplelocalize.cli.processor.ProjectProcessor;
import io.simplelocalize.cli.processor.ProjectProcessorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;


class ShellCommander {

  private static final String DEFAULT_CONFIG_FILE_NAME = "./simplelocalize.yml";
  private Logger log = LoggerFactory.getLogger(ShellCommander.class);

  private ConfigurationLoader configurationLoader;
  private ConfigurationValidator configurationValidator;

  ShellCommander() {
    this.configurationLoader = new ConfigurationLoader();
    this.configurationValidator = new ConfigurationValidator();
  }

  void run(String[] args) throws IOException, InterruptedException {

    String configurationFilePath = resolveConfigurationPath(args);

    Configuration configuration = configurationLoader.load(configurationFilePath);
    configurationValidator.validate(configuration);

    String projectType = configuration.getProjectType();
    String searchDir = configuration.getSearchDir();

    ProjectProcessor projectProcessor = ProjectProcessorFactory.createForType(projectType);
    ProcessResult result = projectProcessor.process(Paths.get(searchDir));

    Set<String> keys = result.getKeys();
    List<Path> processedFiles = result.getProcessedFiles();
    log.info("Found {} unique keys in {} components", keys.size(), processedFiles.size());

    Set<String> ignoredKeys = configuration.getIgnoredKeys();
    keys.removeAll(ignoredKeys);

    String uploadToken = configuration.getUploadToken();
    SimpleLocalizeClient client = new SimpleLocalizeClient(uploadToken);
    client.sendKeys(keys);
  }

  private String resolveConfigurationPath(String[] args) {
    if (args.length > 0) {
      return args[0];
    }
    return DEFAULT_CONFIG_FILE_NAME;
  }

}


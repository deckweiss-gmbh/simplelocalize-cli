package io.simplelocalize.cli.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import io.simplelocalize.cli.client.dto.DownloadRequest;
import io.simplelocalize.cli.client.dto.DownloadableFile;
import io.simplelocalize.cli.client.dto.ExportResponse;
import io.simplelocalize.cli.client.dto.UploadRequest;
import io.simplelocalize.cli.exception.ApiRequestException;
import io.simplelocalize.cli.io.FileWriter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SimpleLocalizeClient
{
  private static final String PRODUCTION_BASE_URL = "https://api.simplelocalize.io";

  private static final List<String> SINGLE_FILE_FORMATS = List.of("multi-language-json", "csv-translations", "excel", "csv");
  private static final String ERROR_MESSAGE_PATH = "$.msg";
  private final HttpClient httpClient;
  private final SimpleLocalizeHttpRequestFactory httpRequestFactory;
  private final SimpleLocalizeUriFactory uriFactory;

  private final Logger log = LoggerFactory.getLogger(SimpleLocalizeClient.class);
  private final FileWriter fileWriter;
  private final ObjectMapper objectMapper;

  public SimpleLocalizeClient(String baseUrl, String apiKey)
  {

    Objects.requireNonNull(baseUrl);
    Objects.requireNonNull(apiKey);
    this.uriFactory = new SimpleLocalizeUriFactory(baseUrl);
    this.httpRequestFactory = new SimpleLocalizeHttpRequestFactory(apiKey);
    this.objectMapper = new ObjectMapper();
    this.fileWriter = new FileWriter();
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMinutes(5))
            .build();
  }

  public static SimpleLocalizeClient withCustomServer(String baseUrl, String apiKey)
  {
    return new SimpleLocalizeClient(baseUrl, apiKey);
  }

  public static SimpleLocalizeClient withProductionServer(String apiKey)
  {
    return withCustomServer(PRODUCTION_BASE_URL, apiKey);
  }

  public void sendKeys(Collection<String> keys) throws IOException, InterruptedException
  {
    URI uri = uriFactory.buildSendKeysURI();
    HttpRequest httpRequest = httpRequestFactory.createSendKeysRequest(uri, keys);
    HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    throwOnError(httpResponse);
    int keysProcessed = JsonPath.read(httpResponse.body(), "$.data.uniqueKeysProcessed");
    log.info(" 🎉 Successfully uploaded {} keys", keysProcessed);
  }

  public void uploadFile(UploadRequest uploadRequest) throws IOException, InterruptedException
  {
    Path uploadPath = uploadRequest.getPath();
    URI uri = uriFactory.buildUploadUri(uploadRequest);
    HttpRequest httpRequest = httpRequestFactory.createUploadFileRequest(uri, uploadRequest);
    log.info(" 🌍 Uploading {}", uploadPath);
    HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    throwOnError(httpResponse);
  }


  public void downloadFile(DownloadRequest downloadRequest) throws IOException, InterruptedException
  {
    URI downloadUri = uriFactory.buildDownloadUri(downloadRequest);
    HttpRequest httpRequest = httpRequestFactory.createGetRequest(downloadUri).build();
    String downloadPath = downloadRequest.getPath();
    log.info(" 🌍 Downloading to {}", downloadPath);
    HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    throwOnError(httpResponse);
    byte[] body = httpResponse.body();
    String languageKey = downloadRequest.getLanguageKey();
    boolean isRequestedTranslationsForSpecificLanguage = StringUtils.isNotEmpty(languageKey);
    String downloadFormat = downloadRequest.getFormat();
    boolean isFileFormatWithAllLanguages = isSingleFileFormat(downloadFormat);
    if (isRequestedTranslationsForSpecificLanguage || isFileFormatWithAllLanguages)
    {

      Optional<Path> directoryPath = Optional.of(downloadPath).map(Path::of).map(Path::getParent);
      if (directoryPath.isPresent())
      {
        Files.createDirectories(directoryPath.get());
      }

      Files.write(Path.of(downloadPath), body);
    } else
    {
      fileWriter.saveAsMultipleFiles(downloadPath, body);
    }

    log.info(" 🎉 Download success!");
  }

  public List<DownloadableFile> fetchDownloadableFiles(DownloadRequest downloadRequest) throws IOException, InterruptedException
  {
    URI downloadUri = uriFactory.buildDownloadUriV2(downloadRequest);
    HttpRequest httpRequest = httpRequestFactory.createGetRequest(downloadUri).build();
    HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    throwOnError(httpResponse);
    String body = httpResponse.body();
    ExportResponse exportResponse = objectMapper.readValue(body, ExportResponse.class);
    return exportResponse.getFiles();
  }

  public void downloadFile(DownloadableFile downloadableFile, String downloadPath)
  {
    String url = downloadableFile.getUrl();
    Path savePath = Paths.get(downloadPath, downloadableFile.getProjectPath());
    HttpRequest httpRequest = httpRequestFactory.createGetRequest(URI.create(url)).build();
    try
    {
      log.info(" 🌍 Downloading to {}", savePath);
      Files.createDirectories(savePath.getParent());
      httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofFile(savePath));
    } catch (IOException e)
    {
      log.error(" 😝 Download failed: {}", savePath, e);
    } catch (InterruptedException e)
    {
      log.error(" 😝 Download interrupted: {}", savePath, e);
      Thread.currentThread().interrupt();
    }
  }

  public int validateGate() throws IOException, InterruptedException
  {
    URI validateUri = uriFactory.buildValidateGateUri();
    HttpRequest httpRequest = httpRequestFactory.createGetRequest(validateUri).build();
    HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    throwOnError(httpResponse);
    String json = httpResponse.body();
    Boolean passed = JsonPath.read(json, "$.data.passed");
    String message = JsonPath.read(json, "$.data.message");
    int status = JsonPath.read(json, "$.data.status");
    log.info(" 🌍 Gate result: {} (status: {}, message: {})", passed, status, message);
    return status;
  }

  private boolean isSingleFileFormat(String downloadFormat)
  {
    return SINGLE_FILE_FORMATS.stream().anyMatch(format -> format.equalsIgnoreCase(downloadFormat));
  }

  private void throwOnError(HttpResponse<?> httpResponse)
  {
    if (httpResponse.statusCode() != 200)
    {
      com.jayway.jsonpath.Configuration parseContext = com.jayway.jsonpath.Configuration
              .defaultConfiguration()
              .addOptions(Option.SUPPRESS_EXCEPTIONS);

      Object responseBody = httpResponse.body();
      String stringBody = safeCastHttpBodyToString(responseBody);
      String message = JsonPath.using(parseContext).parse(stringBody).read(ERROR_MESSAGE_PATH);
      if (message == null)
      {
        message = "Unknown error, HTTP Status: " + httpResponse.statusCode();
      }
      log.error(" 😝 Request failed: {}", message);
      throw new ApiRequestException(message, httpResponse);
    }
  }

  private String safeCastHttpBodyToString(Object responseBody)
  {
    if (responseBody instanceof byte[])
    {
      return new String((byte[]) responseBody);
    } else if (responseBody instanceof String)
    {
      return (String) responseBody;
    }
    return "";
  }


}

package io.simplelocalize.cli.io;

import io.micronaut.core.util.AntPathMatcher;
import io.simplelocalize.cli.client.dto.FileToUpload;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.simplelocalize.cli.TemplateKeys.LANGUAGE_TEMPLATE_KEY;
import static io.simplelocalize.cli.TemplateKeys.NAMESPACE_TEMPLATE_KEY;

public class FileListReader
{

  public List<FileToUpload> findFilesToUpload(String uploadPath) throws IOException
  {
    List<FileToUpload> output = new ArrayList<>();

    String beforeTemplatePart = getParentDirectory(uploadPath);
    Path parentDir = Path.of(beforeTemplatePart);

    boolean exists = Files.exists(parentDir);
    if (!exists)
    {
      String parentDirectory = StringUtils.substringBeforeLast(beforeTemplatePart, "/");
      parentDir = Path.of(parentDirectory);
    }

    try (Stream<Path> foundFilesStream = Files.walk(parentDir, 6))
    {
      AntPathMatcher antPathMatcher = new AntPathMatcher();
      String uploadPathPattern = uploadPath
              .substring(uploadPath.equals(beforeTemplatePart) ? 0 : beforeTemplatePart.length())
              .replace(LANGUAGE_TEMPLATE_KEY, "**")
              .replace(NAMESPACE_TEMPLATE_KEY, "**");
      var foundFiles = foundFilesStream
              .filter(Files::isRegularFile)
              .filter(path -> antPathMatcher.matches(uploadPathPattern, path.toString())) // .replace('\\', '/') !!!!!!!!!!!!!!!!!!!!!!!!
              .collect(Collectors.toList());
      for (Path foundFile : foundFiles)
      {
        Map<String, String> templateValues = extractTemplateValues(uploadPath, foundFile);

        String languageKey = templateValues.get(LANGUAGE_TEMPLATE_KEY.replaceAll("^.|.$", ""));
        String namespace = templateValues.get(NAMESPACE_TEMPLATE_KEY.replaceAll("^.|.$", ""));

        FileToUpload fileToUpload = FileToUpload.FileToUploadBuilder.aFileToUpload()
                .withLanguage(StringUtils.trimToNull(languageKey))
                .withNamespace(StringUtils.trimToNull(namespace))
                .withPath(foundFile).build();
        output.add(fileToUpload);
      }
      return output;
    }
  }

  private String getParentDirectory(String uploadPath)
  {
    int languageTemplateKeyPosition = uploadPath.indexOf(LANGUAGE_TEMPLATE_KEY);
    int namespaceTemplateKeyPosition = uploadPath.indexOf(NAMESPACE_TEMPLATE_KEY);
    String[] splitUploadPath = StringUtils.splitByWholeSeparator(uploadPath, LANGUAGE_TEMPLATE_KEY);
    if (namespaceTemplateKeyPosition > 0 && languageTemplateKeyPosition > 0)
    {
      if (languageTemplateKeyPosition < namespaceTemplateKeyPosition)
      {
        splitUploadPath = StringUtils.splitByWholeSeparator(uploadPath, LANGUAGE_TEMPLATE_KEY);
      } else
      {
        splitUploadPath = StringUtils.splitByWholeSeparator(uploadPath, NAMESPACE_TEMPLATE_KEY);
      }
    }

    if (splitUploadPath.length == 0)
    {
      throw new IllegalStateException("Unable to find parent directory for upload path");
    }

    return splitUploadPath[0];
  }

  private Map<String, String> extractTemplateValues(String uploadPath, Path file) {
    Matcher m = Pattern.compile("\\{(\\w*?)}").matcher(uploadPath);
    Map<String, String> vars = new LinkedHashMap<>();
    while (m.find()) {
      vars.put(m.group(1) , null);
    }

    String pattern = uploadPath;
    // escape regex special characters
    pattern = pattern.replaceAll("([?.])", "\\\\$1");
    for (String var : vars.keySet()) {
      // replace placeholders with capture groups
      pattern = pattern.replaceAll("\\{" + var + "}", "([\\\\w\\\\s]+?)");
    }

    m = Pattern.compile(pattern).matcher(file.toString().replace('\\', '/'));
    if (m.matches()) {
      int i = 0;
      for (String var : vars.keySet()) {
        vars.put(var, m.group(++i));
      }
    }

    return vars;
  }

//    String regex = uploadPath.replace('{')
//
//    Pattern issuePattern = Pattern.compile("(?<project>[A-Z]{3})(?<sep>[-/])(?<org>\\w{3})\\k<sep>(?<num>\\d+)$");
//
//    // Create Matcher with a string value.
//    Matcher issueMatcher = issuePattern.matcher(file.toString().replace('\\','/'));
//
//    // We can use capturing group names to get group.
//    return issueMatcher.group(templateKey);


//    String afterTemplatePart = splitUploadPath[1];
//    String fileName = file.getFileName().toString();
//    String output = StringUtils.remove(file.toString().replace('\\','/'), beforeTemplatePart);
//    output = StringUtils.remove(output, afterTemplatePart);
//    output = StringUtils.remove(output, fileName);
//    output = output.replace(File.separator, "");
//    output = StringUtils.remove(output, File.separator);
//    return output.trim();
}

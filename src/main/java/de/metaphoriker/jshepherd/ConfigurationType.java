package de.metaphoriker.jshepherd;

/** Enumeration holding the types of configuration formats available. */
public enum ConfigurationType {
  YAML("yml", ":"),
  PROPERTIES("properties", "=");

  private final String extension, delimiter;

  ConfigurationType(String extension, String delimiter){
    this.extension = extension;
    this.delimiter = delimiter;
  }

  public String getExtension() {
    return extension;
  }

  public String getDelimiter() {
    return delimiter;
  }
}

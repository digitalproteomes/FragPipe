package com.dmtavt.fragpipe.api;

import com.github.chhh.utils.StringUtils;
import java.util.Objects;
import java.util.StringJoiner;

public class UpdatePackage {
  public final String downloadUrl;
  public final String propertyName;
  public final String description;
  public final String minVersion;
  public final String maxVersion;

  public UpdatePackage(String downloadUrl, String propertyName, String description,
      String minVersion, String maxVersion) {
    Objects.requireNonNull(propertyName, "prop name can't be null");
    Objects.requireNonNull(downloadUrl, "download url can't be null");
    this.downloadUrl = downloadUrl;
    this.propertyName = propertyName;
    this.description = description;
    this.minVersion = minVersion;
    this.maxVersion = maxVersion;
  }

  public String getDescriptionOrName() {
    return StringUtils.isBlank(description) ? propertyName : description;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", UpdatePackage.class.getSimpleName() + "[", "]")
        .add("propertyName='" + propertyName + "'")
        .add("downloadUrl='" + downloadUrl + "'")
        .add("description='" + description + "'")
        .add("minVersion='" + minVersion + "'")
        .add("maxVersion='" + maxVersion + "'")
        .toString();
  }
}
